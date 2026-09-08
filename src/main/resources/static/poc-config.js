// Public config ONLY. Never put real credentials here.
window.POC_CONFIG = {
  BACKEND_URL: window.location.origin + '/vnpt-proxy',
  // Confirmed from web-sdk-version-3.2.1.0.js: every URL is
  // BACKEND_URL + this relative path + ?challengeCode=... .
  endpoints: {
    ENDPOINT_UPLOAD_IMAGE: '/file-service/v1/addFile',
    ENDPOINT_LIVENESS_DOCUMENT: '/ai/v1/web/card/liveness',
    ENDPOINT_LIVENESS_FACE: '/ai/v1/web/face/liveness-3d',
    ENDPOINT_MASKED_FACE: '/ai/v1/web/face/mask',
    ENDPOINT_COMPARE_FACE: '/ai/v1/web/face/compare',
    ENDPOINT_OCR_DOCUMENT: '/ai/v1/web/ocr/id',
    ENDPOINT_OCR_DOCUMENT_FRONT: '/ai/v1/web/ocr/id/front'
  }
};
