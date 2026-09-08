# Review và bằng chứng — 2026-09-08

## Nguồn đã đọc trước khi sửa

Đọc toàn bộ PDF được cung cấp: `TÀI LIỆU HƯỚNG DẪN TÍCH HỢP SDK eKYC WEB - ver 3.2.1.pdf`.
Tên file nói 3.2.1, bìa ghi V3.2.0.0, lịch sử thay đổi ghi v3.1.0.0.
Vì chưa có bundle nên không thể xác nhận version SDK chỉ bằng tên PDF.
Đã đọc toàn bộ source/config/test/README hiện có. Bundle 3.2.1.0 được bổ sung sau review.

## Review implementation cũ → thay đổi

| Thành phần | Trước | Sau / lý do |
|---|---|---|
| Frontend | HTML do Spring phục vụ, tham chiếu SDK 2.1.0 nhưng thiếu file | Giữ HTML cùng origin; dùng `vnpt-sdk/web-sdk-version-3.2.1.0.js` và lib thực tế |
| Khởi tạo | FaceVNPTBrowserSDK.init + ekycsdk.init | window.SDK.launch theo ví dụ HTML mới |
| Backend URL | http://localhost:8080/vnpt-proxy | location.origin + /vnpt-proxy; tại localhost cùng giá trị; dùng được khi chuyển HTTPS |
| Auth browser | dummy TOKEN_ID/TOKEN_KEY/AUTHORIZATION | thử empty TOKEN_ID/TOKEN_KEY/ACCESS_TOKEN, dropdown dummy sau reload |
| Auth backend | env VNPT_AUTHORIZATION, hardcode token_id/token_key | env VNPT_ACCESS_TOKEN; bundle xác nhận `Authorization: Bearer`, `Token-id`, `Token-key` |
| Flow | FLOW_TYPE DOCUMENT, USE_WEBCAM, USE_UPLOAD | SDK_FLOW DOCUMENT_TO_FACE, USE_METHOD PHOTO theo bảng PDF |
| Callback | callback positional của ekycsdk.init | CALL_BACK trong config theo §2.3/§3, chỉ cập nhật UI, không log payload |
| Custom endpoint | không có | đủ bảy path default trong poc-config.js; bundle chứng minh BACKEND_URL + endpoint |
| Proxy | wildcard /vnpt-proxy/**, byte stream WebClient | giữ; bổ sung loại credential aliases/cookies và hop-by-hop Connection tokens, log request trước kiểm tra env |
| Test | JSON/multipart mock | thêm encoded path/query, PUT/GET, byte nhị phân, upstream 401/204, auth override và thiếu config |
| Chạy | README gọi wrapper chưa tồn tại | tạo Gradle Wrapper 8.14.3, .env.example và hướng dẫn source env |

Credential thật chưa được ghi vào frontend hoặc source backend. Code cũ cũng dùng env cho
credential thật nhưng không chứng minh SDK hoạt động với dummy. Kết luận đó vẫn chưa có.

## Điều PDF xác nhận / điều chưa xác nhận

- §2.2: BACKEND_URL có thể trỏ URL khách hàng; không có slash cuối theo §2.4.2.
- §2.2: TOKEN_ID, TOKEN_KEY, ACCESS_TOKEN là config author API.
- §2.2: liệt kê đủ ENDPOINT_UPLOAD_IMAGE, ENDPOINT_LIVENESS_DOCUMENT,
  ENDPOINT_LIVENESS_FACE, ENDPOINT_MASKED_FACE, ENDPOINT_COMPARE_FACE,
  ENDPOINT_OCR_DOCUMENT, ENDPOINT_OCR_DOCUMENT_FRONT và mô tả có thể sửa endpoint.
- **Không có** default endpoint cụ thể, quy tắc nối URL, HTTP method, request headers,
  multipart field contract hay cách serialize ACCESS_TOKEN vào HTTP header.
- §2.4.2 dùng tên AUTHORIZON và nói không cần bearer. Không suy ra được header HTTP
  không cần Bearer: SDK có thể tự thêm prefix; đoạn này còn khác tên ACCESS_TOKEN.
- Bảng dùng SDK_FLOW/ENABLE_API_*/CALL_BACK_END_FLOW; ví dụ dùng
  FLOW_TAKEN/CHECK_*/CALL_BACK. Các dòng 45–46 còn lặp tên endpoint với mô tả bật/tắt.
  PoC chọn flow/flags theo bảng và launch/callback theo ví dụ, có ghi chú trong code.
  Đây là lựa chọn tạm theo nguồn, chưa phải xác nhận bundle thực sự hiểu các tên đó.
- Không có bằng chứng SDK validate empty, tạo chữ ký bằng credential hoặc cho phép dummy.
- Phần response schema §4 mô tả kết quả SDK, không chứng minh raw response mỗi API.

Vì vậy chưa điền custom endpoints và không đổi wildcard proxy thành endpoint nghiệp vụ đoán.
Mapping header Token-id/Token-key và prefix trong .env.example là ứng viên cấu hình,
không được xem là kết luận từ PDF. VNPT_AUTH_CONTRACT_CONFIRMED mặc định false.

## Bundle inspection

`web-sdk-version-3.2.1.0.js` thực tế xác nhận:

- POST `${BACKEND_URL}${ENDPOINT_UPLOAD_IMAGE}?challengeCode=...`: multipart FormData,
  part `file`, `title=upload_file`, `description=ic_upload_file`.
- POST các endpoint liveness/OCR/compare/mask: `application/json`.
- Header `Authorization: Bearer ${ACCESS_TOKEN}`, `Token-id`, `Token-key`, `mac-address: WEB-001`.
- Default paths: `/file-service/v1/addFile`, `/ai/v1/web/card/liveness`,
  `/ai/v1/web/face/liveness-3d`, `/ai/v1/web/face/mask`, `/ai/v1/web/face/compare`,
  `/ai/v1/web/ocr/id`, `/ai/v1/web/ocr/id/front`.

## Runtime SDK/VNPT

Kiểm tra cục bộ đã thực hiện:

- `./gradlew test --console=plain`: BUILD SUCCESSFUL, 5 tests, 0 failures/errors.
- `node --check` cho poc.js và poc-config.js: hợp lệ cú pháp; không phải chạy SDK.
- `./gradlew bootRun --console=plain`: Spring Boot 3.5.11 khởi động trên Java 21.0.10,
  Netty port 8080.
- HTTP GET `http://localhost:8080/`: 200.
- Lần kiểm tra trước khi có bundle: HTTP GET `/sdk-web.js`: 404. Đã khắc phục bằng bundle
  3.2.1.0 ở `/vnpt-sdk/`; cần chạy lại browser sau khi ứng dụng restart.

Chưa có browser Network evidence hay VNPT credentials test. Không có VNPT request thực tế
để ghi nhận response.
Không có VNPT request thực tế nào để liệt kê method/path/body/response.
Không dùng các URL upload/OCR trong test để khẳng định SDK gọi các URL đó.

| Acceptance | Kết quả |
|---|---|
| Custom BACKEND_URL/custom ENDPOINT works | UNKNOWN |
| SDK request reaches our backend | UNKNOWN |
| Backend proxies request to VNPT | UNKNOWN |
| VNPT response returns through backend | UNKNOWN |
| SDK accepts proxied response | UNKNOWN |
| Real VNPT credentials can stay backend-side | UNKNOWN |

UNKNOWN = chưa có bằng chứng; không ghi NO khi chưa chạy được.

## Mẫu ghi nhận sau khi bổ sung SDK

Ghi riêng mỗi lần empty/dummy, version và SHA-256 bundle, thời điểm, loại flow:

| Bằng chứng | Giá trị cần ghi |
|---|---|
| Bước SDK / Initiator | file/hàm tạo request |
| HTTP method + URL | URL browser thực tế, raw path/query đã che giá trị nhạy cảm |
| Custom endpoint | tên config, giá trị đã điền và cách bundle nối URL |
| Request headers | tên header, Content-Type; credential chỉ ghi empty/dummy/redacted |
| Request body | schema/field names, multipart part names/MIME, không ảnh/base64 |
| Backend Incoming | timestamp + method/path |
| Backend Forwarding | timestamp + method/upstream path |
| VNPT response | HTTP status + Content-Type + schema/mã lỗi đã che PII |
| Browser response | status/schema khớp response qua proxy |
| SDK bước kế tiếp | chuyển màn/chụp tiếp/callback hoặc lỗi rõ ràng |

Nếu chỉ có upload thành công nhưng liveness chưa chạy thì ghi phạm vi đó, không PASS toàn flow.
Nếu ảnh bị chặn trên thiết bị thì chưa có bằng chứng transport.
Không lưu HAR nguyên bản chứa ảnh/PII vào repo.
