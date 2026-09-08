document.addEventListener('DOMContentLoaded', () => {
  const notice = document.getElementById('notice');
  const start = document.getElementById('start');
  if (!window.SDK || typeof window.SDK.launch !== 'function') {
    notice.textContent = 'Thiếu SDK mới: đặt sdk-web.js, companion scripts và lib/ ngang hàng index.html. Kiểm tra Network 404 và Console.';
    return;
  }
  notice.textContent = 'SDK.launch có sẵn. Bảy custom endpoint đang trỏ qua backend PoC; mở Network trước khi bắt đầu.';
  start.disabled = false;
  start.addEventListener('click', async () => {
    start.disabled = true;
    const credential = document.getElementById('credentials').value;
    const endpoints = Object.fromEntries(Object.entries(window.POC_CONFIG.endpoints).filter(([, value]) => value !== ''));
    try {
      await window.SDK.launch({
        BACKEND_URL: window.POC_CONFIG.BACKEND_URL,
        URL_WEB_OVAL: '/vnpt-sdk/lib/web-oval.json',
        URL_MOBILE_OVAL: '/vnpt-sdk/lib/mobile-oval.json',
        ...endpoints,
        TOKEN_ID: credential, TOKEN_KEY: credential, ACCESS_TOKEN: credential,
        // Confirmed from the supplied 3.2.1.0 bundle.
        SDK_FLOW: 'DOCUMENT_TO_FACE', USE_METHOD: 'PHOTO',
        ENABLE_API_LIVENESS_DOCUMENT: true,
        ENABLE_API_LIVENESS_FACE: true,
        ENABLE_API_MASKED_FACE: true,
        ENABLE_API_COMPARE_FACE: true,
        HAS_RESULT_SCREEN: true, HAS_QR_SCAN: false,
        SHOW_TAB_RESULT_QRCODE: false, DEFAULT_LANGUAGE: 'vi',
        // The supplied 3.2.1.0 bundle reads CALL_BACK_END_FLOW. Never log its payload.
        CALL_BACK_END_FLOW: () => { notice.textContent = 'SDK đã gọi CALL_BACK_END_FLOW. Chưa phải VERIFIED/PASS; đối chiếu Network và log backend.'; }
      });
    } catch (_) {
      notice.textContent = 'SDK.launch lỗi. Kiểm tra Console/Network tại máy. Reload để thử dummy nếu empty bị từ chối.';
    }
  });
});
