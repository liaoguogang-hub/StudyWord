# 版本历史

本项目版本号遵循 [语义化版本 2.0](https://semver.org/lang/zh-CN/) 规范。
格式参考 [Keep a Changelog 1.1](https://keepachangelog.com/zh-CN/1.1.0/)。

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
