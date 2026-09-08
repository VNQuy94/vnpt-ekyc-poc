# PoC VNPT Web SDK → backend → VNPT

HTML/JS thuần do Spring Boot phục vụ cùng origin. Java 21, Spring Boot 3, Gradle,
WebClient. Không cần Node, npm, frontend dev server hay database.

## Kết luận hiện tại

**Chưa có runtime VNPT thật**: bundle SDK 3.2.1.0 đã nằm trong project và đã được inspect,
nhưng chưa có credential test/Network trace browser/VNPT response thật. Không có request
VNPT thất bại để quy lỗi endpoint/auth.

```text
Custom BACKEND_URL/custom ENDPOINT works: UNKNOWN
SDK request reaches our backend: UNKNOWN
Backend proxies request to VNPT: UNKNOWN
VNPT response returns through backend: UNKNOWN
SDK accepts proxied response: UNKNOWN
Real VNPT credentials can stay backend-side: UNKNOWN
```

UNKNOWN ở năm dòng đầu nghĩa là chưa thử được, không phải NO (đã thử và thất bại).
Test mock chỉ chứng minh transport, không đủ PASS flow thật.
Xem [review và bằng chứng](docs/evidence.md) để phân biệt điều PDF xác nhận và điều còn thiếu.

## Prerequisites

- Java JDK 21; kiểm tra `java -version`.
- Bash trên Linux/macOS cho các lệnh dưới đây.
- Gradle Wrapper 8.14.3 đã có trong repo; lần đầu cần mạng để tải Gradle/dependencies.
- Browser có camera: thử Chrome/Edge trên desktop tại localhost trước.
- Không cần Node, npm, pnpm hay yarn.
- Bundle 3.2.1.0 và `lib/` đã có trong `src/main/resources/static/vnpt-sdk/`.
  Không cần Node, npm, pnpm hay yarn.

Vị trí SDK hiện tại (thư mục static là web root):

```text
src/main/resources/static/
  index.html                         ← giữ trang PoC
  poc-config.js
  poc.js
  vnpt-sdk/web-sdk-version-3.2.1.0.js
  vnpt-sdk/lib/                       ← nguyên thư mục lib VNPT
```

File demo `dist/index.html` không được di chuyển vì có credential trong đó. Bundle/lib được
Git ignore để tránh vô tình commit SDK proprietary.

## Environment variables

Tại terminal:

```bash
cd /home/yugui/Documents/vnpt-ekyc-proxy-poc
cp -n .env.example .env
```

Mở .env bằng editor và điền:

```dotenv
VNPT_BASE_URL=https://api.idg.vnpt.vn
VNPT_TOKEN_ID='giá trị VNPT cấp'
VNPT_TOKEN_KEY='giá trị VNPT cấp'
VNPT_ACCESS_TOKEN='giá trị VNPT cấp'
```

VNPT_BASE_URL là upstream, không phải URL backend PoC. Xác nhận URL môi trường với VNPT.
.env không được phục vụ cho browser và đã được Git ignore.
Spring không tự load .env: cần source theo bước chạy phía dưới.

Bundle `web-sdk-version-3.2.1.0.js` đã xác nhận các header outgoing:

```dotenv
Token-id: <TOKEN_ID>
Token-key: <TOKEN_KEY>
Authorization: Bearer <ACCESS_TOKEN>
```

`.env.example` đã đặt mapping đó. `VNPT_ACCESS_TOKEN` phải là token không gồm `Bearer`.
Backend trả 503 khi thiếu credentials. Không dùng `VNPT_AUTHORIZATION` cũ.

## Kiểm tra SDK/custom endpoint trước khi test

Trong DevTools → Sources, mở `vnpt-sdk/web-sdk-version-3.2.1.0.js`, nhấn pretty-print {} rồi tìm:

```text
BACKEND_URL
ENDPOINT_UPLOAD_IMAGE
ENDPOINT_LIVENESS_DOCUMENT
ENDPOINT_LIVENESS_FACE
ENDPOINT_MASKED_FACE
ENDPOINT_COMPARE_FACE
ENDPOINT_OCR_DOCUMENT
ENDPOINT_OCR_DOCUMENT_FRONT
Token-id
Token-key
Authorization
ACCESS_TOKEN
```

Theo dõi nơi biến được dùng để build URL/request, không chỉ sự xuất hiện của tên biến.
Ghi version/checksum của bundle. Xác nhận full URL hay path được nối với BACKEND_URL,
HTTP method, header names, prefix auth và body JSON/FormData của từng call.

`poc-config.js` đã điền đủ bảy custom endpoint. Bundle xác nhận nó build URL theo
`BACKEND_URL + endpoint + ?challengeCode=...`, vì thế URL browser phải bắt đầu bằng
`http://localhost:8080/vnpt-proxy/`. Các path đang dùng là default path lấy từ bundle,
không phải path suy đoán từ tài liệu.

## Start backend

```bash
cd /home/yugui/Documents/vnpt-ekyc-proxy-poc
set -a
source .env
set +a
./gradlew bootRun
```

Backend chạy port **8080**. Giữ terminal mở; Ctrl+C để dừng.
Nếu sửa env, dừng server, source lại .env và chạy lại.
Nếu sửa static assets khi bootRun đang chạy, restart để processResources copy bản mới.

## Start frontend

Không có command riêng. Spring phục vụ frontend ở **http://localhost:8080/**.
Mở URL này sau khi backend chạy. Không mở index.html bằng file://.
Tại localhost, BACKEND_URL tính ra đúng http://localhost:8080/vnpt-proxy, không có slash cuối.

## Cách test thật

1. Mở http://localhost:8080/. Nhấn F12 hoặc Ctrl+Shift+I → Network → Preserve log.
   Kiểm tra All để thấy thiếu asset, rồi Fetch/XHR để theo dõi API. Reload trang.
2. Nếu báo thiếu SDK, xử lý 404 của script/lib trước; chưa có request API ở bước này.
3. Chọn **Empty**, nhấn **Bắt đầu eKYC**. Cho phép camera, chụp mặt trước/sau
   giấy tờ và khuôn mặt theo hướng dẫn SDK. Flow yêu cầu trong config là DOCUMENT_TO_FACE.
4. Nếu SDK từ chối empty trước khi request: ghi thông báo và Network, reload,
   chọn **Dummy**, thử lại. Không đưa credential thật vào frontend để ép PASS.
5. Với mỗi API request, ghi method, raw URL/path/query, tên header, Content-Type,
   body schema (field names, kiểu, multipart part name/MIME; không ảnh/base64),
   response HTTP status, Content-Type, schema/mã lỗi đã che dữ liệu. Xem tab Initiator.
6. URL API mong đợi bắt đầu http://localhost:8080/vnpt-proxy/…
   Nếu gọi api.idg.vnpt.vn trực tiếp, dừng và xác định custom endpoint/URL building.
7. Đối chiếu timestamp, method/path giữa Network và ba dòng terminal:

```text
Incoming SDK request: POST /vnpt-proxy/<path-thực-tế>
Forwarding: POST https://api.idg.vnpt.vn/<path-thực-tế>
VNPT response: HTTP 200
```

8. Network → Response phải nhận response của upstream qua URL localhost đó.
   SDK chuyển bước tiếp theo và cuối flow có CALL_BACK. Callback chỉ cập nhật UI,
   không lưu dữ liệu, không update VERIFIED và không phải kết luận đáng tin cậy độc lập.
9. Ghi kết quả vào docs/evidence.md. Backend đã trực tiếp nhận response là bằng chứng
   của hop VNPT → backend; HTTP 200 một mình chưa chứng minh SDK chấp nhận kết quả.

Ảnh tờ giấy trắng có thể bị chặn ngay trong SDK trước khi gọi API. Nếu tới VNPT
và trả lỗi, chỉ chứng minh được hop transport đó; chưa chứng minh SDK tiếp tục flow.
Dùng giấy tờ/khuôn mặt hợp lệ do bạn được phép sử dụng hoặc dữ liệu test VNPT chấp nhận
để kiểm chứng toàn bộ flow.

## Expected result / PASS

Tất cả API của flow đã chạy đi qua backend; không có API gọi thẳng VNPT.
Backend nhận request, forward và nhận response; Network thấy response tại URL PoC;
SDK tiếp tục bình thường. Empty hoặc dummy hoạt động xuyên suốt thì mới kết luận
credentials thật có thể ở backend. Nếu có bước ký dữ liệu hoặc validation cần secret,
ghi request/bước đó và kết luận PARTIAL/NO theo bằng chứng.

## Test transport tự động

```bash
./gradlew test
```

Không cần .env hay SDK thật. Dùng MockWebServer với dữ liệu giả.
Kiểm tra JSON, multipart, byte nhị phân, encoded path/query lặp, method POST/PUT/GET,
response 201/200/401/204, thay header auth, loại alias browser, và 503 khi chưa cấu hình.
Report: build/reports/tests/test/index.html. Các path trong test là fixture transport,
không phải danh sách endpoint SDK thực tế.

## Proxy và giới hạn

/vnpt-proxy/** → bỏ prefix → VNPT_BASE_URL + raw path + raw query.
Body stream không parse OCR/liveness/compare hay rebuild multipart.
Giữ status/body/content-type; bỏ hop-by-hop headers (kể cả tên trong Connection),
credential headers và cookies ở ranh giới proxy.
Không tự follow redirect; nếu upstream trả redirect, inspect Location để phát hiện
khả năng browser đi thẳng VNPT. Log không chứa query/body/token.
Nếu SDK nhúng auth vào query/body hoặc ký dữ liệu, proxy chưa xử lý việc đó:
phải inspect và ghi nhận, không thể tuyên bố backend-only chỉ từ test thay header.

## Troubleshooting

| Triệu chứng | Kiểm tra / xử lý |
|---|---|
| CORS | Frontend/backend mặc định cùng origin nên không cần CORS. Kiểm tra request có gọi thẳng VNPT hoặc khác port không. Giữ cùng origin thay vì thêm wildcard. |
| Camera không mở | Cho phép camera trong browser/OS; đóng ứng dụng đang giữ camera. localhost trên desktop thường được dùng cho camera; IP LAN HTTP trên mobile cần HTTPS. |
| VNPT 401/403 | Đã có Forwarding và VNPT response: kiểm tra token hết hạn, tên header/prefix theo contract, URL và quyền API/IP allowlist nếu VNPT yêu cầu. Không đổi token ở browser. |
| 503 | Điền đủ ba credentials và xác nhận mapping bằng VNPT_AUTH_CONTRACT_CONFIRMED=true; source lại .env rồi restart. |
| 502 | Lỗi kết nối upstream/DNS/TLS; xem loại lỗi transport trong terminal; kiểm tra mạng và VNPT_BASE_URL. |
| Sai BACKEND_URL | Không có slash cuối; phải trỏ origin PoC + /vnpt-proxy. BASE_URL env vẫn là VNPT. |
| Sai custom ENDPOINT | Kiểm tra URL bị nối đôi, thiếu prefix, absolute URL bị nối với base; đối chiếu chỗ build URL trong bundle. |
| SDK/lib 404 | Kiểm tra `vnpt-sdk/web-sdk-version-3.2.1.0.js` và `vnpt-sdk/lib/` còn nguyên trong static web root. Restart sau khi thay bundle. |
| Không có request dù nhấn bắt đầu | Kiểm tra SDK.launch tồn tại, lỗi asset/khởi tạo, empty validation hoặc kiểm tra ảnh trên thiết bị. Thử dummy sau reload và ghi bằng chứng. |
| SDK bỏ qua flow/callback config | PDF có tên mâu thuẫn; inspect bundle xác định SDK_FLOW/FLOW_TAKEN và CALL_BACK/CALL_BACK_END_FLOW. Không thêm cả hai để che vấn đề. |

## Mobile / HTTPS

Camera cần secure context; localhost được chấp nhận, còn IP LAN HTTP không tương đương
localhost. Xem [MDN getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).
Hướng dẫn certificate và trust trên mobile: [mkcert chính thức](https://github.com/FiloSottile/mkcert).

Cách trực tiếp, không cần Docker: dùng HTTPS của chính Spring, với certificate được
điện thoại tin cậy cho IP LAN của máy. Ví dụ khi đã có mkcert và OpenSSL:

```bash
mkcert -install
mkcert -cert-file /tmp/vnpt-poc.pem -key-file /tmp/vnpt-poc-key.pem localhost 127.0.0.1 YOUR_LAN_IP
openssl pkcs12 -export -in /tmp/vnpt-poc.pem -inkey /tmp/vnpt-poc-key.pem -out /tmp/vnpt-poc.p12 -name vnpt-poc
export SERVER_SSL_ENABLED=true
export SERVER_SSL_KEY_STORE=file:/tmp/vnpt-poc.p12
export SERVER_SSL_KEY_STORE_TYPE=PKCS12
read -rs -p 'PKCS12 password: ' SERVER_SSL_KEY_STORE_PASSWORD
export SERVER_SSL_KEY_STORE_PASSWORD
./gradlew bootRun
```

Thay YOUR_LAN_IP bằng IP thật, cài/trust rootCA.pem của mkcert trên điện thoại
(theo quy trình trust của OS); chỉ chuyển certificate CA công khai, không chuyển
rootCA-key.pem. Mở https://YOUR_LAN_IP:8080/ trên cùng Wi-Fi, cho phép port qua firewall.
poc-config.js dùng location.origin nên proxy đi qua HTTPS cùng origin, không bị mixed content.
Các bước HTTPS/mobile này chưa được chạy trong môi trường hiện tại.
