# Auto Fighter AI Safe Helper — Fabric 1.21.3

## Tính năng
- Nhấn **G** để bật/tắt.
- Cảnh báo khi phát hiện mob thù địch trong 20 block.
- Cảnh báo khi máu còn 10 HP trở xuống.
- Không tự ngắm, không tự nhảy và không tự tấn công.

## Build
Dùng Gradle với Java 21: `gradle --no-daemon clean build`.
JAR sau remap: `build/libs/AutoFighterAI-1.21.3-SafeHelper-0.3.0.jar`.

## Cài
Cần Minecraft 1.21.3, Fabric Loader và Fabric API tương thích. Chép JAR vào `.minecraft/mods`.

CI kiểm tra build và cấu trúc JAR; vẫn nên thử trong game thực tế.
