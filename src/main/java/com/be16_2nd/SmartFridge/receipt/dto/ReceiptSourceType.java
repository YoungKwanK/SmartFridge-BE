package com.be16_2nd.SmartFridge.receipt.dto;

/**
 * 인식한 사진 종류
 * - RECEIPT: 종이 영수증
 * - ORDER_SCREEN: 쇼핑앱 주문내역 화면 캡처
 * - UNKNOWN: 영수증이 아니거나 알아볼 수 없음
 */
public enum ReceiptSourceType {
    RECEIPT,
    ORDER_SCREEN,
    UNKNOWN
}
