# Auto Fighter AI — Fabric 1.21.3

## Tính năng
- Nhấn **G** để bật/tắt.
- Tìm mob thù địch trong 20 block, tự hướng camera về thân mob và tấn công khi trong tầm cận chiến.
- Xác suất nhảy khi vào nhịp đánh là 30%; nếu không chọn nhảy thì đánh bình thường.
- Khi máu còn 10 HP trở xuống, thử ăn thức ăn từ hotbar, tối đa 2 lượt mỗi lần bật.

## Build
Dùng Gradle với Java 21: `gradle --no-daemon clean build`.
JAR đã remap nằm trong `build/libs/AutoFighterAI-1.21.3-0.1.0.jar`.

## Cài
Cần Fabric Loader, Fabric API và Minecraft 1.21.3. Chép JAR vào `.minecraft/mods`.

Lưu ý: CI xác nhận build và kiểm tra cấu trúc JAR; hành vi trong game vẫn cần thử trên Minecraft thật.
