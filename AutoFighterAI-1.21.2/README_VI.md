# Auto Fighter AI — Fabric 1.21.2

## Tính năng
- Nhấn **G** để bật/tắt.
- Tìm mob thù địch trong 20 block, tự hướng camera về thân mob và tấn công khi trong tầm cận chiến.
- Xác suất nhảy khi vào nhịp đánh là 30%; nếu không chọn nhảy thì đánh bình thường.
- Khi máu còn 10 HP trở xuống, thử ăn thức ăn từ hotbar, tối đa 2 lượt mỗi lần bật.

## Build
Chạy bằng Gradle với Java 21: `gradle --no-daemon clean build`.
JAR phát hành đã remap nằm trong `build/libs/AutoFighterAI-1.21.2-0.1.0.jar`.

## Cài
Cần Fabric Loader, Fabric API và Minecraft 1.21.2. Chép JAR vào thư mục `.minecraft/mods`.

Bản build tự động được chạy bằng GitHub Actions; kiểm thử logic trong game vẫn cần thực hiện trên Minecraft thật.
