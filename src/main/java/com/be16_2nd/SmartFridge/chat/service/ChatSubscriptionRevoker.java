package com.be16_2nd.SmartFridge.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.List;

/*
 * 채팅방에서 빠진 회원의 STOMP 구독을 서버에서 끊는다
 * - 참여자 확인은 SUBSCRIBE 때 한 번뿐이라, 나가거나 강퇴돼도 구독이 남아 있으면 연결이 끊길 때까지 메시지를 계속 받는다
 * - 구독 목록은 ChatPresence(Redis)에 있으므로 거기서 그 회원의 구독을 찾아 지우고,
 *   구독이 어느 서버에 붙어 있는지 모르므로 Redis 채널로 모든 서버에 알려 각 서버의 브로커에서 구독을 해제한다
 */
@Slf4j
@Component
public class ChatSubscriptionRevoker implements MessageListener {

    public static final String CHANNEL = "chat-revoke-channel";
    private static final String SEPARATOR = ",";

    private final ChatPresence chatPresence;
    private final StringRedisTemplate redisTemplate;
    private final MessageChannel brokerChannel;

    public ChatSubscriptionRevoker(ChatPresence chatPresence,
                                   @Qualifier("chatPubSub") StringRedisTemplate redisTemplate,
                                   @Qualifier("brokerChannel") MessageChannel brokerChannel) {
        this.chatPresence = chatPresence;
        this.redisTemplate = redisTemplate;
        this.brokerChannel = brokerChannel;
    }

    // 참여자 제거가 커밋된 뒤에만 끊는다 (롤백되면 그대로 둔다)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void revoke(ChatSubscriptionRevoked event) {
        List<ChatPresence.Subscription> subscriptions =
                chatPresence.removeSubscriptions(event.type(), event.roomId(), event.email());
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = String.join(SEPARATOR, subscriptions.stream().map(ChatPresence.Subscription::field).toList());
        redisTemplate.convertAndSend(CHANNEL, payload);
    }

    // redis -> 서버: 이 서버의 브로커에서 구독 해제 (이 서버에 없는 세션이면 브로커가 무시한다)
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        for (String field : payload.split(SEPARATOR)) {
            ChatPresence.Subscription subscription = ChatPresence.Subscription.parse(field);
            if (subscription != null) {
                unsubscribe(subscription);
            }
        }
    }

    private void unsubscribe(ChatPresence.Subscription subscription) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(subscription.sessionId());
        accessor.setSubscriptionId(subscription.subscriptionId());
        brokerChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        log.info("채팅방에서 빠진 회원의 구독 해제 session={} subscription={}", subscription.sessionId(), subscription.subscriptionId());
    }
}
