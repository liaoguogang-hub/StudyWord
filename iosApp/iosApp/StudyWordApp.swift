import SwiftUI
import Shared   // KMP shared 模块的 iOS 编译产物,Phase 1 起会引入

@main
struct StudyWordApp: App {
    var body: some Scene {
        WindowGroup {
            // v1.7.0 (Phase 0):先用 SwiftUI 自己的占位验证 iOS 应用能起来。
            // Phase 1 起替换为 SharedKitStudyWordSplash(...) —— 直接调用
            // KMP 模块里的 Compose composable。
            Phase0Placeholder()
        }
    }
}

/// Phase 0 SwiftUI 占位,确认 SwiftUI 渲染路径通畅。
struct Phase0Placeholder: View {
    var body: some View {
        ZStack {
            Color(red: 1.0, green: 0.894, blue: 0.71).ignoresSafeArea()
            VStack(spacing: 16) {
                Text("识字小帮手")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(Color(red: 0.4, green: 0.2, blue: 0.0))
                Text("Phase 0 · iOS 链路验证")
                    .font(.system(size: 18))
                    .foregroundColor(.gray)
            }
        }
    }
}

#Preview {
    Phase0Placeholder()
}
