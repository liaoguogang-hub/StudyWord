package com.studyword.literacy.data

import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter
import com.studyword.literacy.util.PinyinConverter
import java.util.LinkedHashSet

class CharacterRepository {

    private val allCharacters: List<LearningCharacter>
    private val grouped: Map<Difficulty, List<LearningCharacter>>

    init {
        val seen = LinkedHashSet<String>()
        val items = mutableListOf<LearningCharacter>()
        var nextId = 0

        fun ingest(rawWords: String, difficulty: Difficulty) {
            parseWords(rawWords).forEach { word ->
                word.forEach { ch ->
                    val hanzi = ch.toString()
                    if (seen.add(hanzi)) {
                        val pinyin = PinyinConverter.toPinyin(hanzi)
                        items += LearningCharacter(
                            id = nextId++,
                            hanzi = hanzi,
                            pinyin = pinyin,
                            difficulty = difficulty
                        )
                    }
                }
            }
        }

        ingest(EASY_WORDS, Difficulty.EASY)
        ingest(MEDIUM_WORDS, Difficulty.MEDIUM)
        ingest(HARD_WORDS, Difficulty.HARD)

        allCharacters = items
        grouped = allCharacters.groupBy { it.difficulty }
    }

    fun all(): List<LearningCharacter> = allCharacters

    fun count(): Int = allCharacters.size

    fun byDifficulty(difficulty: Difficulty): List<LearningCharacter> =
        grouped[difficulty] ?: emptyList()

    private fun parseWords(raw: String): List<String> =
        raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    companion object {
        private val EASY_WORDS = """
爸爸 妈妈 爷爷 奶奶 哥哥 姐姐 弟弟 妹妹 叔叔 阿姨 老师 同学 同桌 同伴 朋友 邻居 家人 宝宝 孩子 伙伴
校园 教室 课堂 课本 练习 书包 钢笔 彩笔 橡皮 尺子 剪刀 胶水 课桌 椅子 黑板 粉笔 作业 本子
写字 读书 画画 唱歌 跳舞 玩耍 游戏 打球 投球 踢球 跑步 散步 走路 爬山 游泳 洗澡 洗手 洗脸 刷牙 梳头
起床 睡觉 午睡 早饭 午餐 晚饭 点心 牛奶 豆浆 鸡蛋 米饭 面条 馒头 包子 饺子 米粥 菜汤 汤面 汤圆 糖果
苹果 香蕉 梨子 桃子 葡萄 草莓 西瓜 柚子 石榴 蓝莓 樱桃 猕猴桃 哈密瓜 香瓜 柠檬 山楂 杨梅 荔枝 桑葚
青菜 白菜 菠菜 西红柿 黄瓜 茄子 土豆 红薯 芋头 南瓜 冬瓜 苦瓜 豆角 豆芽 韭菜 香菜 生菜 洋葱 蘑菇 木耳
小狗 小猫 小鸡 小鸭 小鹅 小猪 小马 奶牛 黄牛 绵羊 山羊 兔子 松鼠 小熊 熊猫 老虎 狮子 豹子 狐狸 灰狼
大象 长颈鹿 猴子 猩猩 海豚 鲸鱼 海狮 海豹 海鸥 乌龟 金鱼 小虾 螃蟹 海星
太阳 月亮 星星 云朵 蓝天 白云 彩虹 春风 秋风 夏雨 冬雪 雪花 雨滴 雷声 闪电 雾气 露珠 朝霞 晚霞 晴天 阴天 多云 微风 暴雨 台风
家里 图书馆 操场 宿舍 食堂 厨房 客厅 卧室 阳台 花园 公园 游乐场 超市 商店 书店 文具 医院 药店 银行 邮局 车站
公交车 出租车 私家车 自行车 滑板车 摩托车 火车 高铁 地铁 飞机 热气球 轮船 渡船 渔船 小船 游轮 车票 站台 信号
春节 元旦 元宵 清明 端午 中秋 重阳 国庆 儿童 生日 蛋糕 蜡烛 礼物 贺卡 烟花 鞭炮 花灯 灯笼 彩灯
红色 黄色 蓝色 绿色 紫色 橙色 粉色 白色 黑色 灰色 棕色 金色 银色 圆形 正方形 长方形 三角形 星形 心形 菱形
开心 高兴 快乐 舒服 满意 伤心 难过 担心 生气 害怕
拥抱 牵手 握手 点头 摇头 跺脚 弯腰 伸手 挥手 转身
开门 关门 开灯 关灯 电视 电脑 手机 平板 电话 相机 照片 影集 书籍 报纸 杂志 音乐 歌曲 玩具 积木""".trimIndent()

        private val MEDIUM_WORDS = """
思考 研究 实验 探索 发现 整理 总结 分析 计划 方法 安排 目标 任务 步骤 日程 方案 策略 记录 报告 调查
访谈 问卷 反馈 建议 修改 校对 审核 批准 归档 备案
博物馆 科技馆 美术馆 音乐厅 剧院 体育场 体育馆 科技园 植物园 动物园 天文馆 少年宫 科学课 历史课 地理课 数学课 美术课 音乐课 社会课 语文课
互联网 网站 网页 浏览 搜索 下载 上传 登录 注册 账户 密码 邮箱 信息 信号 传输 网络 数据 数据库 程序 代码 算法 调试 编译 运行 执行 控制 系统 平台 应用 版本 更新 升级 维护 操作 监控 安全 防护 密钥 加密 解密
机器人 智能 传感 芯片 模块 电路 电源 电压 电流 电池 充电 插座 灯泡 开关 控制器 遥控 信号灯 感应 触摸 屏幕
影像 视频 音频 广播 新闻 报道 记者 编辑 主播 观众 播放 暂停 回放 剧集 片段 片头 字幕 配音 角色 台词
表演 导演 演员 摄影 摄像 后期 剪辑 特效 布景 道具 服装 化妆 舞台 灯光 音响 麦克 音律 节奏 乐器 琴声 钢琴 小提琴 大提琴 吉他 竖琴 长笛 单簧管 萨克斯 小号 圆号 长号 架子鼓 木琴 手鼓 铃鼓 合唱 独唱 独奏 伴奏 节拍
舞蹈 民族舞 古典舞 芭蕾 街舞 拉丁 踢踏舞 舞步 排练 彩排 编舞 纹样 图案 设计 草图 线稿 上色 水彩 油画 素描 雕塑 陶艺 剪纸 手工 编织 布偶 纺织 缝纫 刺绣 织布 纺车 织机 作品 展览 讲解 解说 导览 参观 拍照 留念
旅行 行程 导游 景点 门票 售票 检票 候车 候机 登机 航班 机舱 起飞 降落 安检 海关 护照 签证 行李 托运 寄存 取件 旅店 宾馆 民宿 青旅 前台 服务 叫醒 退房 结账 收据 发票 汇率 兑换 存钱 取款 柜员 信用 储蓄
社区 志愿 公益 捐赠 环保 节约 资源 能源 燃料 电力 水源 水质 监测 报警 装置 仪表 温度 湿度 气压 风速 预报 预测 雷达 卫星 云图 气象 气候 暴雪 暴雨 台风 龙卷风 沙尘暴 海啸 地震 火山 熔岩 岩浆 岩石 峡谷 山脉 高原 平原 盆地 河流 湖泊 海湾 海峡 海岛 珊瑚 潮汐""".trimIndent()

        private val HARD_WORDS = """
宇宙 星系 星云 星团 星座 星轨 行星 卫星 彗星 流星 黑洞 白矮星 中子星 脉冲星 太阳风 太阳系 引力 引力波 光速 光谱 光年 光子 粒子 质子 中子 电子 原子 分子 晶体 晶格 晶体管 半导体 超导 量子 量子态 能级 波函数 概率 方程 算符 矢量 张量 矩阵 行列式 微积分 导数 积分 极限 微分 偏导 梯度 迭代 递归 算法 模型 模拟 仿真 优化 复杂度 机器 学习 深度 神经网络 训练 权重 损失 激活 拟合 泛化 正则 监督 无监督 强化 机器人 自动化 传感器 执行器 控制论 信息论 熵值 互信息 编码 解码 信道 噪声 冗余 校验 密钥 哈希 区块 链条 分布式 共识 节点 算力 峰值 延迟 带宽 服务器 数据库 事务 索引 查询 缓存 并发 线程 协程 死锁 互斥 垃圾回收 虚拟机 容器 编排 调度 集群 网格 云端 边缘 雾计算 物联网 嵌入式 固件 芯片组 总线 协议 传输层 应用层 加密层 证书 签名 鉴权 隧道 防火墙 安全域 隔离区 沙箱 审计 追踪 取证 溯源 漏洞 修补 热补丁 版本迭代 灰度发布 蓝绿发布 金丝雀 回滚 监控器 指标 可视化 仪表盘 告警 告警等级 阈值 预测 分析 大数据 数据湖 数据仓库 字段 维度 度量 透视 抽样 清洗 标签 聚类 分类 回归 精度 召回 曲线 混淆 熵图 可解释性 决策树 随机林 梯度提升 支持向量 贝叶斯 马尔科夫 卡尔曼 蒙特卡罗 仿真器 模拟器 统计量 方差 协方差 分布 正态 泊松 二项 指数 对数 渐近 泰勒 傅里叶 拉普拉斯 变换 微分方程 偏微分 边界 条件 初值 稳态 混沌 分形 分岔 吸引子 李雅普诺夫 列维飞行 复杂系统 自组织 涌现 网络科学 小世界 无标度 熵增 熵减 自然数 整数 有理数 实数 复数 向量场 标量 张量场 散度 旋度 格林 斯托克斯 高斯 麦克斯韦 电磁 波动方程 场强 通量 洛伦兹力 洛伦兹变换 狭义相对论 广义相对论 时空 引力场 闵科夫斯基 黎曼几何 测地 曲率 奇点 彭罗斯 史瓦西 卡西米尔 泡利 海森堡 薛定谔 波函数 本征值 本征态 叠加 纠缠 隧穿 退相干 退激发 基态 激发态 玻色 费米 玻色子 费米子 量子场 真空态 量子泡沫 量子引力
翱 翮 旻 昊 曜 熠 炽 烁 煜 煦 煨 熔 熵 燧 燮 爝 珑 璨 璟 璇 璞 璜 璐 瓒 琨 琰 琛 珞 珂 琅 琦 瑶 瑾 瑜 瑗 瑛 珣 珩 珺 珮 琚 琥 玥 玮 玱 玢 玳 玺
镭 镱 锫 锬 镓 镍 镎 镨 镝 镢 镤 镥 镧 镰 飒 飓 飕 霁 霭 霾 霰 霹 靥 靓 鳍 鳗 鳕 鳞 鲲 鲳 鲟 鲼 鼯 鼬 鼷 黛 黝 黠 黢 黯 瀚 瀛 瀹 灏 潋 澜 涟 嶙 峋 岱 岚 嵘 嵛 彧 罡 颢 颍 颛 颉 颌 擎 擢 攫 攥 攒 擘 擂 璁 璘 璺""".trimIndent()
    }
}
