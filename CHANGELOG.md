# 版本历史

本项目版本号遵循 [语义化版本 2.0](https://semver.org/lang/zh-CN/) 规范。
格式参考 [Keep a Changelog 1.1](https://keepachangelog.com/zh-CN/1.1.0/)。

## [1.4.2] - 2026-10-03

本版本针对 v1.4.1 的 4 项遗留 UX 问题逐项处理 + 8 张文生图提示词。

### 新增
- ✂️ **删主页头部 chrome** — 删 `titleText`("🌈 识字小帮手")+ `subtitle`("一起开启有趣的识字冒险！")
  - `englishSubModeGroup` marginTop 6→0dp,卡片首屏即可见完整:汉字 + 拼音 + 难度 + 词组 + 例句 + 4 emoji 按钮
  - 顶部只剩右上角汉堡按钮(≡),空白处变清爽
- 🎨 **抽屉全面重设计** — 不再是 "chip + 3 个灰按钮"
  - 顶部 greeting 卡:随机动物 emoji + "你好呀!" 问候 + 副标题,3D 糖果风
  - "🌍 学习设置" 合并卡:语言 + 难度 chip 同卡,减少视觉割裂
  - 3 张大入口卡(学习进度 / 字库浏览 / 更多设置),每张含:圆形图标底 + 标题 + 描述 + ›
    蓝紫渐变 / 粉橘渐变 / 薄荷渐变 区分功能
  - 新增 5 个 drawable:`bg_drawer_avatar`、`bg_drawer_nav_icon`、`bg_drawer_nav_{blue,pink,mint}`
- 🔙 **跳转源页"← 返回"按钮** — 字库 / 进度点击跳转后,主页顶部左侧显示"← 返回进度" 或 "← 返回字库"
  - ProgressActivity / CharacterLibraryActivity `setResult` 时新增 `EXTRA_SOURCE_PAGE` 标记源页
  - MainActivity 收到后保存 `jumpSource`,显示按钮
  - 点击重启动源 Activity + 关闭主页(不会因 stack 空而退出 app)
  - 正常启动时不显示按钮,不占位
- 🗣️ **中文整段正常语速朗读**
  - `DEFAULT_SPEECH_RATE` 0.7f → 1.0f(用户反馈"词组例句读得慢、卡")
  - `speakPhraseCharByChar` 改为薄包装,内部直接走 `speak()` 整段朗读
  - `speakCurrentItem` 中文字不再 append 拼音(否则 "日 rì" 被读两遍),拼音仅作为卡片下方的 visual hint

### 文生图提示词(交付)
- 2 张开机图英文 prompt,用于 splash 真实图片上线:
  - **Prompt 1 — 故事书主题**:3D 卡通熊猫坐在大打开的故事书上,周围漂浮认 / 字 / 大 / 家 立体字块
  - **Prompt 2 — 字母动物主题**:3D 兔子戴巫师帽挥出金色闪光,周围 A B C 字母块 + 天 字块
  - 详见会话上下文(本次对话顶部)

### 兼容
- `versionCode 7 → 8`,`versionName 1.4.1 → 1.4.2`
- `EXTRA_SOURCE_PAGE` 是 result intent 新增 extra,旧接收方忽略无副作用
- 数据层完全兼容 v1.4.1,无任何数据迁移

## [1.4.3] - 2026-10-04

本版本针对 v1.4.2 之后的 8 项用户反馈继续打磨 UI 细节、修复 MaterialButton 关键 bug、补齐缺失的英文功能。

### 新增
- 🇨🇳🇬🇧 **主页顶部语言 · 难度 标签** — 替换 v1.4.2 英文 mode 时压在卡片上方的"字母/单词"子模式 chip
  - `topLabel` TextView 位于 ConstraintLayout 左上角,显示 `🇨🇳 中文 · 难度 简单` / `🇬🇧 ENGLISH · 难度 简单`
  - 中英文 mode 卡片位置完全一致(因为子模式 chip 不再顶在卡片上方)
  - `refreshTopLabel()` 在语言 / 难度切换、`jumpSource` 变化时刷新文本 + 动态 marginStart(避免和"← 返回"按钮重叠)
- 📚 **抽屉"子模式"chip 组** — 英文 mode 子模式 chip 从主页搬入抽屉"学习设置"卡
  - 3 组 chip(语言 / 难度 / 子模式)用同一个 `Widget.StudyWord.DifficultyChip`,大小完全一致 40dp 高
  - 英文 mode 才显示子模式;中文 mode 自动隐藏(`englishSubModeLabel.visibility = GONE`)
  - 抽屉顶部 greeting 卡高度自适应,3 组 chip 不再溢出
- 🎯 **字库英文难度过滤** — 字库总览英文 mode 现在按难度过滤 word(letter 不过滤)
  - `difficultyFilterGroup` 在英文 mode 始终可见,不再仅显示"全部难度"
  - 新增 `matchesDifficultyEnglish(item)`:letter 始终显示,word 按 `DifficultyFilter` 过滤
  - "困难"只剩字母 + 困难单词(nose / mouth / hair / day / night / happy / love / red / blue / green)
  - "简单"只剩字母 + 简单单词(cat / dog / apple / ball / sun / moon / star / fish / bird / milk)
- 🎬 **游戏 0.7 秒延迟 + 中文只读一遍汉字**
  - `showCurrentQuestion()` 中 LISTEN / LISTEN_LETTER / LISTEN_WORD 模式:
    `binding.root.postDelayed({ speakCurrentPrompt() }, 700L)` — 给孩子一点准备时间再发音
  - `speakCurrentPrompt()` 中文分支只读 `q.correct.character.hanzi`,不再 append 拼音
    (v1.4.2 之前 "日 rì" 被 TTS 读两遍,体验不好)
  - 英文 letter / word 走 `TtsManager.speakEnglish`,语速 1.0f
- 🎨 **3D 糖果按钮** — 主页 / 设置 / 游戏页所有按钮统一升级为深色渐变 + 圆角 24dp
  - `bg_button_primary`:`deep_blue → deep_blue → tangerine` 顶到底渐变,白字清晰
  - `bg_button_unknown`:`#A8341F → tangerine → tangerine` 深红橘,白字清晰
  - 去掉 v1.4.1 的天空蓝中段(白字看不清)
  - `bg_button_secondary` 保留浅薄荷绿底 + 深蓝字
- ✨ **整体 UI 提升** — 字号 / 间距 / 圆角统一梳理
  - 字卡 padding 24dp,emoji 与字之间 margin 12dp,卡圆角 28dp
  - 抽屉 greeting 卡 + 3 张入口卡 圆角统一 28dp
  - 主页 4 emoji 按钮圆角 24dp,与卡片风格一致

### 修复
- 🔧 **MaterialButton 点击失效**(v1.4.1 → v1.4.2 遗留严重 bug)
  - 现象:主页 😊/😢/➡️/🎮 4 个按钮、设置中心"导出学习进度"/"重置中文"/"重置英文" 3 个按钮显示正常但点击没反应
  - 根因:`Widget.MaterialComponents.Button.TextButton` 父样式 + `app:backgroundTint="@null"` 时,
    内部 `MaterialShapeDrawable` 拦截触摸事件,logcat 报 `InputTransport detect abnormal consume, count is 0`
  - 修复:按钮样式改用 `Widget.MaterialComponents.Button.UnelevatedButton` 父样式,
    同时显式声明 `backgroundTint=@null` / `android:backgroundTint=@null` / `stateListAnimator=@null` / `elevation=0dp`,
    才能让 layer-list 背景生效并正常接收点击
  - 影响:导出 CSV、重置进度、主页"换一个" / "进入游戏" 等按钮从 v1.4.3 起可点击
- 🔙 **返回按钮布局** — 字库 / 进度跳转卡片后,主页左上角"← 返回"按钮与 topLabel 重叠
  - `refreshTopLabel()` 根据 `jumpSource` 动态设置 `marginStart`:
    `if (jumpSource == null) 0 else dp(140)`(140dp 留给返回按钮)
- 🚪 **返回按钮导致退出 app** — 主页 finish() 后源页左上角箭头 finish() 会直接退出 app
  - `setupBackJumpButton()` 去掉 `finish()`,主页留在背景
  - 任务栈:`[主页, 源页]`,源页左上箭头 → 回到主页,主页 onResume → 仍可见,back 按钮仍可再跳

### 文生图提示词(交付)
- 2 张开机图 **中文详细版** prompt,用于 splash 真实图片上线:
  - **Prompt 1 — 故事书主题**:3D 卡通熊猫坐在大打开的故事书上,周围漂浮 认 / 字 / 大 / 家 立体字块
  - **Prompt 2 — 字母动物主题**:3D 兔子戴巫师帽挥出金色闪光,周围 A B C 字母块 + 天 字块
  - 详见会话上下文(本次对话顶部)

### 兼容
- `versionCode 8 → 9`,`versionName 1.4.2 → 1.4.3`
- `jumpSource` / `englishSubModeLabel` 是 MainActivity 新增字段,无外部依赖
- `CharacterLibraryActivity.matchesDifficultyEnglish` 新增私有方法,无外部 API 变化
- `GameActivity.speakCurrentPrompt` 行为变更(0.7s 延迟 + 中文只读汉字),无外部依赖
- 数据层完全兼容 v1.4.2,无任何数据迁移

## [1.4.1] - 2026-10-03

本版本是一次"体验打磨"补丁,针对 v1.4.0 的 8 个 UX 问题逐项处理。

### 修复
- 🐛 **例句英文 + 中文 TTS 重叠** —— `TtsManager.speakSequential` 内部每段都走 `QUEUE_FLUSH`,导致每一段都打断上一段,英文读一半中文就插进来,两段叠在一起
  - 方案:抽出 `speakInternal(text, isEnglish, utteranceId, flush)`,`speakSequential` 只在第一项用 `QUEUE_FLUSH` 清空旧队列,余项用 `QUEUE_ADD` 串接,引擎按顺序朗读
  - 默认段间隔 `delayMs` 由 200ms 提到 500ms,贴合孩子的反应节奏
- 🐛 **灰色按钮中间有一条白条** —— 抽屉里 3 个 SecondaryButton 与主页 "➡️" 显示为"灰色底 + 白色中条"
  - 原因:`Widget.MaterialComponents.Button` 父样式默认有 `app:backgroundTint = colorPrimary`,即使用了 `bg_button_secondary.xml` 也会被父样式 tint 覆盖
  - 方案:主题里 `PrimaryButton / SecondaryButton / UnknownButton / OptionButton` 全部改用 `Widget.MaterialComponents.Button.UnelevatedButton` 父样式,无默认 tint,drawable 完全控制外观
- 🐛 **字库 / 进度页点英文 id 跳转静默失败** —— 点 letter "G" 后跳回主页还是上一个字,点英文单词也一样
  - 原因:`loadItemById` 只在当前子模式的 pool 中查找,英文 letter id 在 words pool 中找不到
  - 方案:`EnglishRepository.WORD_ID_OFFSET = 1000`,`id < 1000` 视为 letter 自动切到 `LETTERS` 子模式 + 字母难度,`id >= 1000` 视为 word 自动切到 `WORDS` + 当前难度

### 新增
- 🔠 **字母行 Aa 一行展示** —— 之前 letter 卡片是双行大写 + 小写,占纵向 ~120dp
  - 现在大写 80sp 在左、小写 44sp 在右(baseline 对齐),整行约 80dp,首屏可见
  - 点击整行(`letterContainer`)→ 只朗读字母名,不会顺带读 example word 与中文意思
- 🏷️ **英文 word 卡片显示中文意思** —— `wordMeaning` TextView,字号 24sp,与字母卡的 "example word 中文" 视觉一致
- 🖐️ **字库 / 进度页加边滑返回** —— 左边缘 24dp 区域内右滑 → `finish()` 关闭 Activity
  - `GestureDetector.SimpleOnGestureListener.onFling` 监听,`SWIPE_DISTANCE_THRESHOLD=120dp`,`SWIPE_VELOCITY_THRESHOLD=400`
  - 配套保留顶部返回按钮(已在 v1.3.x 加),方便喜欢点按的家长
- 🎨 **3D 糖果卡通风全面重设计** —— 参考 Khan Academy Kids / Duolingo ABC,告别平面感
  - 背景:`bg_main_gradient` 三段渐变,天蓝 → 淡紫 → 蜜桃(135°),全屏柔和
  - 字卡:`bg_card_playful` = 2.5dp 深蓝描边 + 白→薄荷→粉渐变 + 顶部高光,看起来像会发光的糖果
  - 主按钮:`bg_button_primary` = 蓝→深蓝→橘渐变 + 顶部高光
  - 次按钮:`bg_button_secondary` = 深蓝描边 + 白→薄荷渐变
  - 警告按钮:`bg_button_unknown` = 橙→橘→红渐变
  - 所有 `MaterialCardView` 升级 `cardElevation 0 → 6~8dp`、`cornerRadius 26 → 28dp`
  - 右上角汉堡按钮单独加 `app:elevation="6dp"`,立体跳出
- 📐 **主页头部压缩** —— 标题 28sp → 22sp,subtitle 与标题 baseline 对齐合并到一行
  - `paddingTop` 32dp → 16dp,englishSubModeGroup `marginTop` 12dp → 6dp
  - 720p 屏幕上卡片首屏可见 ~75%,不再需要上滑才能看到字卡

### 兼容
- `versionCode 6 → 7`,`versionName 1.4.0 → 1.4.1`
- 数据层完全兼容 v1.4.0,无任何数据迁移
- `english_sets.json` / `characters.json` 内容未改

## [1.4.0] - 2026-10-03

### 新增
- 📂 **右滑抽屉** — 主页右上角新增汉堡菜单,点击打开右侧 DrawerLayout(280dp 宽)
  - 抽屉内容:语言切换 chip、难度切换 chip、3 个导航按钮(学习进度 / 字库 / 更多设置)
  - 主页一屏完整可见卡片 + 4 个底部 emoji 按钮,顶部不再被多个 chip 占据
- 🗣️ **点击卡片发音** — 删除原"听一听"按钮,改为点击卡片任意位置触发 `speakCurrentItem()`
  - 中文:整字/词组/例句按上下文路由到 char-by-char 队列发音
  - 英文 letter / word / 例句分别按规则串发(详见下方)
- ✨ **底部 4 个 emoji 按钮** — 认识(😊)、不认识(😢)、游戏(🎮)、下一个(➡️),每个按钮点击后有中文 Toast 提示
  - 认识啦! / 没关系,下次记住! / 来玩游戏吧! / 下一个!
- 🌐 **英语子模式细分** — 英文 mode 顶部加 chip `[字母][单词]`,单词 mode 加难度 chip `[简单][中等][困难]`
  - 字母模式用 `EnglishRepository.letters()`(26 个均匀分布)
  - 单词模式按当前难度从 `byCategoryAndDifficulty(category, difficulty)` 取子集
- 🔊 **英文复合串发音**
  - 点 letter:`uppercase`(英文) + `exampleWord`(英文) + `exampleWordChinese`(中文)三段串发,200ms 间隔
  - 点 word:`word`(英文) + `chineseMeaning`(中文)两段串发
  - 点 word 例句:`exampleSentence`(英文) + `exampleSentenceTranslation`(中文)两段串发
- 📊 **进度页可跳转** — ProgressActivity 上"认识汉字 / 待巩固汉字 / 英文 known / 英文 review"四个 chip 全部可点
  - 点击 → `setResult(EXTRA_SELECTED_ID, EXTRA_SELECTED_LANG)` + `finish()`,MainActivity 通过 `registerForActivityResult` 接住并 `loadItemById`
  - 跳转过程中自动切换语言(中 → 英 / 英 → 中)
- 📚 **字库页可跳转** — CharacterLibraryActivity 短按 item → 跳回主页对应卡片;长按 item → 弹状态对话框(原行为)
- 🎮 **游戏改进**
  - 字母选项 split view:大写 60sp 在左、小写 36sp 在右(SpannableString AbsoluteSizeSpan,中间 4 空格拉开)
  - 长单词自动缩字:`setAutoSizeTextTypeUniformWithConfiguration(14, 44, 1, SP)`,溢出按钮内不换行
  - 答错反馈三件套:背景变红 + 按钮左右抖动 380ms (ObjectAnimator translationX) + 短震动 80ms (Vibrator / VibratorManager + VibrationEffect)
- 🌅 **开机启动页** — SplashActivity 5 张 splash_*.xml 自动翻页
  - 每张停留 600ms,共 3 秒后启动 MainActivity
  - drawable/splash_1..5.xml 当前是占位纯色(用户将提供 PNG 替换,无需改代码)
  - AndroidManifest LAUNCHER intent-filter 已从 MainActivity 移到 SplashActivity
  - 新增 `VIBRATE` 权限供游戏答错震动使用

### 修复
- 🐛 **中文多音字 TTS bug** — 词组 / 例句第二遍读时把"日子"的"日"读错音
  - 原因:之前的 `toPinyin("日") = "rì"` 加 `_pinyin` 拼接后,引擎在 `QUEUE_FLUSH` 重发时偶然解析为别的音
  - 方案:`TtsManager.speakPhraseCharByChar(text)` 按字拆,逐字 `QUEUE_ADD`,引擎走默认发音(对常用字最准)
  - 同步加 `PinyinConverter` overrides 表修正已知坏 case:`了→le`、`地→dì`、`么→me`、`还→hái`、`便→pián` 等
- 🐛 **跳转回主页失效 bug** — 点 ProgressActivity / CharacterLibraryActivity 上的字 / 字母 / 单词,跳回主页后显示的不是被点击的那一项
  - 原因:`MainActivity.onResume` 无条件调用 `loadNextItem()`,把 `loadItemById` 设置的 currentItem 替换成队列的下一项
  - 修复:仅当 `currentItem == null`(进程被回收 / 首次启动)时才在 onResume 里 `rebuildQueue + loadNextItem`
- 英文单词发音增加 200ms 间隔,避免连读成一团

### 数据
- 26 字母每个新增 `exampleWordChinese`(Apple→苹果、Bus→公交车 等)
- 30 单词每个新增 `difficulty`(EASY / MEDIUM / HARD,各 10 个)与 `exampleSentenceTranslation`(中文翻译)
- `EnglishRepository.byCategoryAndDifficulty(category, difficulty)` API 新增

### 兼容性
- `versionCode 5 → 6`,`versionName 1.3.0 → 1.4.0`
- ProgressStore / SharedPreferences 完全兼容 v1.3.0 数据,新增字段不影响历史记录
- 字库 / 进度页 RecyclerView adapter 增加可选 `onItemLongClicked` 参数(v1.3.0 的 `AllCharactersAdapter` / `AllEnglishItemsAdapter` 调用代码不受影响)

## [1.3.0] - 2026-10-03

### 新增
- 🌐 **中英双语切换** —— 主页顶部新增"中文 / ENGLISH"chip group,持久化到 SharedPreferences
  - 切换语种后立即清空当前学习项,按新语种重新出题池
  - 英文模式自动隐藏"难度选择"(英文只有 LETTERS / WORDS 两类,无需用户选择)
  - 字卡、词组、例句、TTS 朗读全部按语种路由
- 🔤 **英文字母 + 单词卡片**
  - 大写 + 小写双行(字母模式:"Aa" 大字 + 小字)
  - 中文 / 英文 word 模式沿用 60sp 大字
  - 音标 / 拼音共用 `currentPinyin` TextView
  - 英文 word 例句下方追加中文释义(如 "I love my cat. — 猫")
- 🎮 **英文题型**
  - LISTEN_LETTER:听字母名,从 4 个 Aa 字母卡中选出听到的字母
  - LISTEN_WORD:听单词,从 4 个英文单词中选出听到的单词
  - 题目 / 选项区自动按语种切换,英文题朗读走 `TtsManager.speakEnglish(en-US)`
- 📚 **字库英文页**
  - 字库总览加语言切换 chip
  - 英文模式用新 layout `item_english_status.xml`(Aa + 音标 + 状态)
  - 已学 / 待巩固 / 未学习 三态在英文维度独立计算
- 📊 **进度页加英文统计**
  - 总数(26 letters · 30 words)/ Known / Review / Mastery
  - Known / Review chip 列表,letter 显示 "Aa",word 显示 "cat·猫"
- ⚙️ **设置页扩展**
  - 字库总览卡片:中英数字分开显示
  - 重置按钮拆为"重置中文进度" / "Reset English Progress",互不影响
  - CSV 导出文件改名为 `StudyWord_yyyyMMdd.csv`,内含 3 段:中文 / English letters / English words

### 数据模型
- 新增 `model/StudyMode.kt`(CHINESE / ENGLISH)
- 新增 `model/StudyItem.kt` —— sealed interface,把 LearningCharacter / EnglishLetter / EnglishWord 统一为同一形态
  - 三个实现:`ChineseStudyItem` / `EnglishLetterItem` / `EnglishWordItem`
  - UI 层(主页 / 游戏 / 字库 / 进度)全部面向 StudyItem 编程,不再关心语种
- `game/GameMode` 加 `language: StudyMode` 字段,新增 `LISTEN_LETTER` / `LISTEN_WORD` 两个 enum
- `game/GameQuestion` 改为 `correct: StudyItem / options: List<StudyItem>`
- `game/GameRound.difficulty` 改为可空 `Difficulty?`(英文模式不需要)
- `data/EnglishRepository` 加 `findByLetterById` / `findByWordById` 助手,单词 id 偏移 1000 避免与字母冲突
- `data/ProgressStore` 加 `loadLanguage` / `saveLanguage`(中文 / 英文 维度独立持久化)

### 改进
- 主页顶部三个 chip group 现在线性排布:语言 → 难度 → 字卡
- 主页"🎮 玩游戏"按钮按当前语种自动选择默认游戏模式
- 主页鼓励语新增 "Try again!"(英文模式鼓励文案)
- 字卡 emoji 在英文 word 模式显示 🔤(中文模式仍随机动物头像)
- 词组 chip 在英文 letter 模式显示为"🌟 示例词",例句显示为"📖 Example"
- GameActivity 题目区"提示文字"在英文模式动态切换英文提示语

### 兼容
- ProgressStore 复用 v1.2.1 的 SharedPreferences,新增 KEY(known_english_ids / unknown_english_ids / current_language)
- 现有 v1.2.1 中文用户数据(known_ids / unknown_ids / history_entries)完全不受影响
- v1.3.0 首次启动默认 current_language = CHINESE,沿用 v1.2.1 主页布局

## [1.2.0] - 2026-10-03

### 新增
- 🧩 **字卡下方新增"词组"区** —— 把"单字"扩展到"字 + 词",帮助 3-5 岁孩子从"认字"过渡到"组词"
  - 每个常用字配 2~3 个词组(如 "天" -> "天空 / 今天 / 天气")
  - 词组以可点击 chip 形式排列,点击即触发 TTS 朗读(汉字 + 拼音)
  - 数据缺失的字(老字库中暂未手写词组的字)整块区域自动隐藏,不显示空标签
- 📖 **字卡下方新增"例句"区** —— 让字进入"语境",帮助理解字义
  - 每个常用字配 1 条例句(如 "天" -> "今天天气真好。")
  - 例句整行可点击,点击即触发 TTS 朗读(汉字 + 拼音)
- 🗂️ **`scripts/generate_words.py`** —— 一键为字库前 N 个常用字注入词组 + 例句数据
  - 内置 100 个最常见字的 curated 字典,默认覆盖字库前 100 项
  - 支持 `--limit`、`--dry-run` 参数,可重复执行(已转换的条目会自动跳过)

### 数据模型
- 新增 `model/WordEntry.kt`(词组 + 拼音)
- 新增 `model/ExampleSentence.kt`(例句 + 拼音)
- `LearningCharacter` 新增 `words: List<WordEntry>` 与 `examples: List<ExampleSentence>` 字段,**默认值空列表,保证向后兼容**
- `CharacterRepository` 支持两种 JSON 格式并存:旧格式 `"天"` 与新格式 `{"char":"天","words":[…],"examples":[…]}` 都能解析,旧字库无需迁移

### 改进
- 字卡新增 `Widget.StudyWord.WordChip` 样式,沿用 Material chip 风格(浅蓝底 + 深蓝字 + 圆角)
- 字卡内布局紧凑化(emoji/字/拼音/难度之间的间距从 12dp 调到 8dp),为新内容腾出空间

## [1.1.1] - 2026-10-03

### 修复
- 🎮 游戏题池改为"已学过的字(认识 ∪ 待巩固) ∩ 当前难度"
  - 修复 v1.1.0 题池使用全部字库的 bug,导致游戏出现孩子完全没见过的字
  - 未学过任何字时显示空状态:"还没有学过的字,先去主页学习几个汉字,再来玩吧!" + "返回主页" 按钮

## [1.1.0] - 2026-10-03

### 新增
- 🎮 **游戏模式(识字闯关)** —— 把"被动认字"变成"主动闯关"
  - **听音找字**:TTS 自动朗读,孩子从 4 个汉字里选出听到的字
  - **看拼音选字**:屏幕显示拼音,孩子从 4 个汉字里选出对应的字
  - 每轮 5 题,答完给出 ⭐⭐⭐ / ⭐⭐ / ⭐ / 无星 四档评级
  - 全屏撒花动画 + 正向反馈文案(不打孩子自尊)
  - 主页新增 "🎮 玩游戏" 入口按钮
- 🔊 **TTS 朗读支持** —— 主页 "🔊 听一听" 按钮触发,孩子主动听读音

### 改进
- 抽取 `util/TtsManager` 工具类,全局单例 TTS 引擎,主页与游戏页共用
- 主页"换一个"按钮与"听一听"合并为一行,减少竖向占用

## [1.0.0] - 2025-10-15

### 新增
- 3000 常用字字库(简单 1200 + 中等 1000 + 困难 800),按笔画由浅入深
- 测验主页:逐字测验 + 撒花动画反馈
- 学习进度统计页(掌握率 + 折线趋势图 + 难度饼图)
- 设置页:字库总览 + CSV 进度导出 + 一键重置
- 本地存储(SharedPreferences),无需联网、无账号、无广告
