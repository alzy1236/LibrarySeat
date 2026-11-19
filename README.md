# 高校自习室座位预约管理系统

> 毕业设计项目 · 基于 **SpringBoot + Vue3 + MyBatis-Plus + Element Plus + MySQL** 实现
> 题目方向：Web 应用开发、数据可视化

本系统面向在校学生与管理员使用。学生可在线选择自习室座位、取消选座、签到签退、查看自己的选座记录；
管理员负责维护自习室及座位信息、查看选座记录、统计座位使用情况，实现自习室座位线上化管理，
提升自习座位资源利用效率。

---

## 一、功能一览

### 1. 登录与权限（任务书要求 1）

- 统一登录页，**用同一个账号登录，系统自动识别身份**：
  - `admin`（管理员）→ 自动进入 **管理端**
  - `20230001` 等学号（学生）→ 自动进入 **学生端**
- JWT 鉴权 + 拦截器：`/api/admin/**` 仅管理员可访问，学生访问直接返回 403
- 路由守卫：未登录访问任何业务页面一律跳回登录页；**项目启动后默认进入登录页，重启后同样如此**
- 退出登录 / 修改密码 / 管理员重置学生密码

### 2. 自习室与座位维护（任务书要求 2）

- 自习室增删改查，区分 **长期 / 短期** 两种类型
- 座位按“每行 6 个”自动排版生成，可在座位图上直接编辑、停用（维修）
- 座位图实时显示状态：空闲 / 已预约待签到 / 使用中 / 长期固定 / 超时未清场 / 停用

### 3. 学生选座、取消、记录查询（任务书要求 3）

- 每名学生每天只能预约一个座位；取消后可重新选择
- 选座前后端双重校验：座位占用、楼层开放时间、固定座位层使用限制
- 「我的选座记录」支持状态 / 自习室 / 日期区间筛选与分页

### 4. 管理员查看数据 + 图表统计（任务书要求 4）

- 首页数据概览：**今日预约学生数**、今日在座、待签到、已离开、座位使用率、长期座位数、学生总数等
- ECharts 可视化：
  - 近 7 / 14 / 30 天「预约 - 签到 - 签退」趋势折线图
  - 今日各时段签到人数柱状图
  - 今日座位状态分布环形图
  - 各自习室座位使用率横向柱状图
  - 各自习室签到趋势多折线图
- 今日预约学生明细表（含签到 / 签退时间）

### 5. 界面简洁、数据稳定、可部署运行（任务书要求 5）

- 界面参考主流 SaaS 产品（Linear / Vercel / Stripe）的设计语言：**中性灰阶 + 单一品牌蓝**，
  发丝级 1px 边框替代重阴影，6 / 8 / 12px 三档圆角，13px 基础字号与高信息密度布局；
- 统一设计令牌（`styles/global.css`）：颜色、圆角、阴影、侧栏宽度全部由 CSS 变量驱动，换肤只改一处；
- 学生端与管理端共用同一套组件规范，浅色侧边栏 + 粘性顶栏，表格、表单、标签、空状态风格一致；
- Element Plus 组件库，响应式布局，中文本地化；
- 数据统一存于 MySQL，事务保证选座并发安全；
- 前端打包后由 SpringBoot 直接托管，**一条命令即可部署运行**

---

## 二、核心业务规则（按需求定制）

> 本系统面向 **一座独立的自习楼（共 1—5 层，不是图书馆）**：
> **1 层、2 层、5 层** 为普通座位层（`SHORT`），**3 层、4 层** 为固定座位层（`LONG`）。

| 规则 | 说明 | 实现位置 |
| --- | --- | --- |
| 楼层划分 | 1/2/5 层普通座位层；3/4 层固定座位层 | `study_room`（`room_type=LONG/SHORT`） |
| 普通座位层 | **禁止存放个人物品**，每天 **24:00 前必须带走物品离场**，超时记录违规 | `ReservationService` + `SeatScheduleTask#checkShortRoomClear` |
| 固定座位层 | 可存放学习用品，但须持有该层固定座位 | `ReservationService#reserve` |
| **固定座位需审核** | 学生提交申请后状态为 `PENDING`（待审核），**管理员在「申请审核」页通过后才生效（`ACTIVE`）**；可拒绝并填写原因，学生可撤回后重新申请 | `LongTermSeatService#apply / review / withdraw` |
| 固定座位层签到 | 每天 **08:30 前** 完成签到（07:00 起可签到） | `ReservationService#checkIn` |
| 固定座位层签退 | 每晚 **21:00 - 24:00** 完成签退 | `ReservationService#checkOut` |
| 月度考核 | 一个月内**同一天既签到又签退**的有效出勤 **不足 20 天**，次月 1 日 **自动取消固定座位** | `LongTermSeatService#assess` + `SeatScheduleTask` |
| 每月开放 | 每月 1 号 00:00 自动开放当月固定座位申请 | `SeatScheduleTask#openLongTermMonth` |
| 超时未签到 | 固定座位层 08:30 仍未签到的预约自动失效（`TIMEOUT`） | `SeatScheduleTask#expireLongTermCheckIn` |
| **首页签到 / 签退按钮** | 首页只提供「签到」「签退」两个按钮：**已过签到截止时间 → 签到按钮变灰，点击提示“已错过今日签到时间”；未到签退时段 → 签退按钮变灰，点击提示“签退时间还没到”** | `views/student/StudentHome.vue` |
| **实时时间工具** | 通过 **WebSocket 每秒推送服务器时间**，前端据此计算时钟偏差并平滑显示；签到 / 签退时间一律以 **服务器时间** 为准，本机改时间无效 | `TimeWebSocketHandler` + `stores/clock.js` |

> 签到截止、签退时段、考核天数等参数均可在 **管理端 → 系统设置** 中以中文表单修改，保存后立即生效。

---

## 三、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 2.7.18（Java 17） |
| 持久层 | MyBatis-Plus 3.5.7 + 手写 XML Mapper |
| 数据库 | MySQL 8.0（utf8mb4） |
| 认证 | JWT（jjwt 0.11.5）+ 自定义拦截器 |
| 密码加密 | BCrypt（spring-security-crypto） |
| 实时通信 | Spring WebSocket（服务器时间广播） |
| 定时任务 | Spring `@Scheduled`（Cron） |
| 前端框架 | Vue 3.5（Composition API）+ Vite 5 |
| UI 组件库 | Element Plus 2.14 + @element-plus/icons-vue |
| 图表 | ECharts 5.5 |
| 状态管理 / 路由 | Pinia + Vue Router 4（history 模式） |
| HTTP 客户端 | Axios（统一拦截器处理 token / 错误提示） |

---

## 四、目录结构

```
LibrarySeat/
├── start.bat                     # 一键启动：检查 MySQL -> 启动服务 -> 自动打开登录页
├── stop.bat                      # 一键停止系统（释放 8080 端口）
├── dev-start.bat                 # 开发模式（前后端分离 + 热更新）
├── sql/
│   ├── library_seat.sql          # 建库建表 + 初始数据（账号、自习室、座位、公告、参数）
│   └── demo_history.sql          # 近 7 天演示数据（预约 / 签到 / 签退历史，用于图表展示）
├── backend/                      # SpringBoot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/library/seat/
│       │   ├── LibrarySeatApplication.java
│       │   ├── common/           # Result、BusinessException、UserContext、常量
│       │   ├── config/           # WebMvc、MyBatis-Plus、WebSocket、SPA 回退、异常处理
│       │   ├── controller/       # Auth / Student / StudentLongTerm / AdminSeat / AdminUser / System / Public
│       │   ├── dto/              # 请求与返回对象
│       │   ├── entity/           # 8 张表实体
│       │   ├── interceptor/      # AuthInterceptor（JWT + 角色校验）
│       │   ├── mapper/           # MyBatis-Plus Mapper
│       │   ├── service/          # 业务逻辑（预约、签到签退、考核、统计、时钟）
│       │   ├── task/             # 定时任务 + 时间帧广播
│       │   ├── util/             # JwtUtil
│       │   └── websocket/        # 服务器时间 WebSocket 处理器
│       └── resources/
│           ├── application.yml
│           ├── mapper/*.xml      # 复杂 SQL
│           └── static/           # 前端打包产物（由 Maven 打包进 jar）
├── frontend/                     # Vue3 前端
│   ├── vite.config.js            # 打包输出到 backend/src/main/resources/static
│   └── src/
│       ├── api/                  # axios 封装 + 全部接口
│       ├── components/           # EChart、LiveClock
│       ├── layouts/              # StudentLayout / AdminLayout
│       ├── router/               # 路由 + 角色守卫
│       ├── stores/               # auth（登录态）、clock（实时时钟）
│       ├── utils/                # 今日预约状态等工具
│       ├── views/login/          # 登录页
│       ├── views/student/        # 学生端：首页、自习室、座位图、长期座位、记录
│       ├── views/admin/          # 管理端：概览、自习室、座位、预约、长期考核、账号、公告、设置
│       └── views/common/         # 个人中心
├── scripts/                      # 自动化测试脚本（API / 业务规则 / UI / WebSocket）
└── docs/screenshots/             # 运行截图
```

---

## 五、快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0（默认账号 `root`，密码 `123456`，可在 `backend/src/main/resources/application.yml` 修改）
- Node.js 18+（仅开发前端时需要；仅运行系统不需要）

### 2. 初始化数据库

```bash
mysql -uroot -p123456 -e "source sql/library_seat.sql"
mysql -uroot -p123456 -e "source sql/demo_history.sql"   # 可选：演示用历史数据
```

### 3. 启动

**方式一：一键启动（推荐）**

```bat
start.bat          :: 启动系统，就绪后自动打开浏览器进入【登录页】
start.bat init     :: 重新导入演示数据后再启动
stop.bat           :: 停止系统（释放 8080 端口）
```

启动行为说明：

- **重启项目后第一次打开 → 进入登录页**（`http://localhost:8080/login`），不会自动跳回上次停留的页面；
- **普通刷新页面（F5）不会回到登录页**，保持当前页面和登录状态，不会打断操作；
- 判断依据：后端每次启动都会生成唯一“启动标识”写入页面 `<meta name="ls-boot">`，前端对比上一次的标识，变化即视为项目重启；
- 启动完成后会自动打开默认浏览器；关闭 start.bat 窗口不影响系统运行（需要停止时用 `stop.bat`）；
- 后端控制台只输出少量启动信息，详细日志写入 `logs/library-seat.log`。

**方式二：命令行**

```bash
# 打包（会同时把 frontend 已构建的静态资源打进 jar）
mvn -f backend/pom.xml clean package -DskipTests
java -Dfile.encoding=UTF-8 -jar backend/target/library-seat.jar
```

浏览器访问 **http://localhost:8080** → 自动进入登录页。

**方式三：前后端分离开发**

```bat
dev-start.bat
```

前端访问 http://localhost:5173（Vite 已配置 `/api` 代理与 WebSocket 代理到 8080）。

### 4. 演示账号

| 身份 | 账号 | 密码 | 登录后进入 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `123456` | 管理端 `/admin/dashboard` |
| 学生（张伟，已绑定长期座位 R5-01） | `20230001` | `123456` | 学生端 `/student/home` |
| 学生（胡雪，未绑定长期座位） | `20230015` | `123456` | 学生端 `/student/home` |
| 其他学生 | `20230002` ~ `20230020` | `123456` | 学生端 |

---

## 六、数据库设计

| 表名 | 说明 | 关键字段 |
| --- | --- | --- |
| `sys_user` | 用户（管理员 / 学生） | `username` 唯一、`role=ADMIN/STUDENT`、`password`（BCrypt） |
| `study_room` | 自习室 | `room_type=LONG/SHORT`、`capacity`、`open_start/open_end` |
| `seat` | 座位 | `room_id + seat_no` 唯一、`row_no/col_no` 布局、`status` 停用 |
| `seat_reservation` | 预约记录（含签到签退） | `reserve_date`、`status`、`check_in_time`、`check_out_time`、`overstay` |
| `long_term_seat` | 固定座位申请 / 绑定 | `status=PENDING/ACTIVE/REJECTED/CANCELLED`、`apply_time`、`review_time`、`review_remark`、`bind_time` |
| `seat_assessment` | 月度考核记录 | `assess_month`、`checkin_days`、`checkout_days`、`qualified`、`cancelled_binding` |
| `sys_notice` | 公告 | `title`、`content`、`status` |
| `sys_config` | 系统参数 | `checkin.deadline`、`checkout.start`、`long.min.days` 等 |

预约状态流转：

```
选座成功 ──> WAITING(待签到) ──08:30前签到──> SEATED(使用中) ──21:00后签退──> LEFT(已离开)
                 │                                  │
                 ├─学生/管理员取消──> CANCELLED        └─24:00未签退(短期)──> 标记 overstay 违规
                 └─08:30 未签到────> TIMEOUT(超时失效)
```

---

## 七、实时时间工具说明

签到 / 签退时间是否准确是考勤的关键，因此系统采用 **服务器时间为唯一标准**：

1. 后端 `TickBroadcaster` 每 1000ms 通过 WebSocket（`/api/ws/time`）向所有在线客户端广播服务器时间帧；
2. 前端 `stores/clock.js` 收到帧后计算 `服务器时间 - 本机时间` 的偏差，并用高精度计时器平滑推进显示；
3. 即使修改本机系统时间，界面显示的仍是服务器时间；
4. 所有签到 / 签退接口以服务器时间二次校验窗口，前端提示仅供引导；
5. WebSocket 断开时自动重连，并每 30 秒通过 HTTP `/api/system/server-time` 兜底校时。

---

## 八、接口概览

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login` | 登录，返回 token、角色与跳转路径 |
| | `GET /api/auth/info` / `PUT /api/auth/password` | 当前用户 / 修改密码 |
| 系统 | `GET /api/system/server-time`、`GET /api/system/clock` | 服务器时间、签到签退窗口（免登录） |
| | `WS /api/ws/time` | 服务器时间实时推送 |
| 学生 | `GET /api/student/rooms`、`GET /api/student/rooms/{id}` | 自习室列表 / 详情与座位图 |
| | `POST /api/student/reserve`、`/cancel`、`/checkin`、`/checkout` | 选座、取消、签到、签退 |
| | `GET /api/student/today`、`/today/active`、`/reservations` | 今日预约、生效预约、记录分页 |
| | `GET/POST /api/student/long-term/*` | 长期固定座位申请、放弃、考核记录、日历 |
| 管理 | `GET /api/admin/statistics/*` | 概览、趋势、分布、自习室对比 |
| | `/api/admin/rooms`、`/api/admin/seats` | 自习室、座位维护 |
| | `/api/admin/reservations` | 预约记录查询与代签退 |
| | `/api/admin/long-term`、`/api/admin/assessment/run` | 固定座位管理、手动执行月度考核 |
| | `/api/admin/users`、`/api/admin/notices`、`/api/admin/configs` | 账号、公告、系统参数 |

返回格式统一为 `{ code, message, data }`，`code=200` 表示成功；`401` 未登录 / 登录失效；`403` 无权限。

---

## 九、自动化测试

`scripts/` 下提供了可重复执行的验证脚本（需先启动后端）：

```bash
node scripts/api-test.mjs          # 20 项接口测试：登录、鉴权、角色权限、选座规则、统计接口
node scripts/review-test.mjs       # 13 项申请审核测试：提交申请 -> 驳回 -> 重新申请 -> 通过 -> 生效 -> 撤回
node scripts/rule-test.mjs         # 8 项业务规则测试：20 天考核判定、取消座位、签到/签退窗口
node scripts/ws-test.mjs           # WebSocket 实时时间帧、SPA 回退
node scripts/static-test.mjs       # 前端静态资源托管、深层路由刷新不 404
node scripts/refresh-keep-login-test.mjs  # 刷新页面保持登录 + 重启项目后回到登录页
node scripts/badge-test.mjs        # 申请审核红点实时性：通过/拒绝一条，红点即减一
node scripts/ui-test.mjs           # 25 项真实浏览器（无头 Edge + CDP）UI 自动化测试
```

---

## 十、常见问题

**Q：启动后页面全是 404？**
A：确认已执行过前端构建（`cd frontend && npm run build`），或直接使用仓库中已打包好的 `library-seat.jar`。

**Q：登录提示“密码错误”？**
A：确认已导入 `sql/library_seat.sql`；所有演示账号密码均为 `123456`。

**Q：签到按钮是灰的？**
A：固定座位层签到时间为 07:00–08:30，签退时间为 21:00–23:59；按钮变灰是正常业务限制（点击会有明确提示），
可在「管理端 → 系统设置」中调整时间窗口。

**Q：考核记录为空？**
A：月度考核在每月 1 日 02:00 自动执行；可进入「管理端 → 长期座位与考核」手动选择月份，
先「预览考核结果」（不修改数据），确认后再「执行考核并取消座位」。
