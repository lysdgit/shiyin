# 拾音 Shiyin

B站音频搜索与播放器(Android 原生应用):搜索 B 站内容播放音频,或粘贴视频 / 收藏夹列表批量播放。深色 / 浅色主题,液态玻璃 dock,听歌记录。

## 功能

- **搜索**:关键词搜视频 / UP主,分页加载;UP主详情页看全部作品、播放全部、一键存为歌单
- **粘贴解析**:BV/av 号、视频链接(含 `?p=N`)、b23.tv 短链、收藏夹链接或 ID;单曲入队,多P视频与收藏夹自动创建歌单
- **歌单**:新建 / 加入曲目(去重)/ 删除 / 导出 JSON;多P视频自动聚合为歌单;新歌单封面自动取自曲目
- **听歌记录**:每次真正播放(切歌 / 自动下一首 / 单曲循环)自动记一条——歌名、播放时间、**播放地点(省 / 市,IP 归属地,免定位权限)**;仅存手机本地不上传;一键重播;支持导出 CSV、可清空,无上限不自动删除
- **播放器**:队列扁平化管理、列表/单曲循环、进度拖拽、倍速(0.75–2x)、睡眠定时、音质三档(切换保留进度)
- **失败处理**:音源 403 自动重新解析重试(保留进度);单曲失败自动跳下一首,连续 3 次失败暂停
- **后台播放**:前台服务 + 系统媒体通知(锁屏/通知栏控制),音频焦点与耳机拔出暂停由 Media3 托管

## 液态玻璃外观

- 底栏为 **dock 悬浮胶囊**(参考 [Kyant0/AndroidLiquidGlass → Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)):内容从面板下滚过,模糊 + vibrancy + 折射(Android 13+ 有完整 lens 折射,12+ 有模糊,11 及以下自动回退实色)
- **选中的 tab 有玻璃滑块**,切换时弹簧动画滑过去;在底栏上**左右滑动**也能切 tab;拖拽时滑块**顺手指方向拉长形变**,松手**弹簧吸附回弹**;图标随滑块靠近**放大**(透过玻璃的折射感)
- **深色 / 浅色主题**可切换(设置 → 主题,即时生效)
- **全参数开放调节**(设置 → 玻璃效果 / 滑块动画):玻璃不透明度 0–100%、模糊强度 0–24dp、滑块动画过冲/速度、拖拽拉伸幅度、图标放大倍率——全部拖动实时预览

## 架构

```
app/src/main/java/com/shiyin/player/
├── MainActivity.kt          # 入口,组装 Compose;安装崩溃日志处理器
├── data/
│   ├── Models.kt            # Track / Galbum / Settings(主题与玻璃参数)/ QueueState
│   ├── AppStore.kt          # 持久化(filesDir JSON):队列 / 歌单 / 设置
│   └── PlayHistory.kt       # 听歌记录存储(时间 / 地点 / 歌曲,无上限不自动删除)
├── bili/
│   └── BiliApi.kt           # B站接口层:搜索(buvid+412重试)/视频信息/playurl 解析/
│                            #   UP主作品(老接口+wbi 签名兜底)/收藏夹/b23.tv/粘贴解析
├── util/
│   ├── IPLocation.kt        # IP 归属地查询(B站 zone 首选 + ipip.net,10 分钟缓存)
│   └── CrashLog.kt          # 崩溃日志落盘,设置页可导出
├── player/
│   ├── PlaybackService.kt   # Media3 MediaSessionService 前台服务
│   │                        #   ResolvingDataSource:占位 URI shiyin://track → 打开时解析直链 + Referer
│   └── PlayerBus.kt         # UI ↔ 服务桥,队列以 AppStore 为唯一真源;切歌时写听歌记录
└── ui/
    ├── Ui.kt                # 应用骨架:dock 玻璃底栏(玻璃滑块/左右滑动切 tab)+ 通用组件
    ├── Theme.kt             # 深色/浅色双主题色系
    ├── Search.kt            # 搜索页 + 粘贴解析弹窗(状态在对象层,切 Tab 不丢)
    ├── Pages.kt             # 歌单 Tab / 歌单详情 / UP主详情
    ├── History.kt           # 听歌记录 Tab(倒序列表/重播/CSV 导出/清空)
    ├── Settings.kt          # 设置 Tab(主题切换/玻璃参数/滑块动画/诊断日志导出)
    └── Player.kt            # 全屏播放页(下滑关闭/倍速/定时/音质)+ 队列抽屉 + 加入歌单弹窗
```

## 播放与音源说明

- **无需任何本地服务器或代理**:播放采用 Media3 `ResolvingDataSource` —— 播放列表使用占位
  URI(`shiyin://track`),真正打开音频流时才调用 playurl 解析 CDN 直链,并为请求附加
  `Referer`(B站 CDN 防盗链要求);直链过期(403)自动重解析,进度保留
- 搜索接口带 `buvid3/buvid4` cookie(首次从 `finger/spi` 获取并缓存)+ 风控 412 自动重试,
  无需登录、无需 wbi 签名(UP主作品列表的 wbi 签名兜底已内置)
- 应用数据(歌单/记录/设置)存储在应用私有目录,卸载即清除;随时可导出 JSON/CSV 备份

## 更新记录

### v2.0(当前)

- **一键重播回归**:听歌记录保存视频标识与封面,可精确重播(含分P)
- **定位接口切换**:改用 B站 zone 接口(国内直连最稳)+ ipip.net 兜底,只记录省、市
- 底栏 dock 交互完善:二级页(歌单详情等)点 tab **立即跳转**;左右滑动切 tab
- 设置页支持滚动;全屏页副控制按钮文字调整

### v1.7 ~ v1.9

- **玻璃滑块拖拽交互**:跟手形变、松手弹簧吸附回弹、图标随滑块靠近放大
- **动画参数开放**(设置 → 滑块动画):过冲 / 速度 / 拉伸 / 图标放大
- 听歌记录 **导出 CSV**(UTF-8 BOM,Excel 直接打开);取消条数上限与自动删除
- 全屏页下滑关闭阈值优化;收起按钮加大;副控制胶囊高光玻璃质感

### v1.5 ~ v1.6

- **底栏手势条避让修复**(迷你条不再被小白条遮挡)
- **深色 / 浅色主题**切换;**玻璃参数开放**(不透明度 / 模糊强度)
- 点 tab 从二级页立即弹回主界面

### v1.0(首发)

- 搜索 / UP主 / 粘贴解析 / 歌单 / 播放器 / 后台通知
- 听歌记录(时间 + 地点)
- 液态玻璃 dock 底栏

## 致谢

B站接口的用法(关键词搜索、`playurl?fnval=16` 解析 DASH 音频流、音质档位选择、`buvid3/buvid4` cookie 获取与 412 风控处理、`wbi` 签名)参考了 [maotoumao/MusicFreePlugins](https://github.com/maotoumao/MusicFreePlugins/tree/master/plugins/bilibili) 的 bilibili 插件实现,在此向 MusicFree 及其插件作者致谢;液态玻璃效果基于 [Kyant0/AndroidLiquidGlass(Backdrop)](https://github.com/Kyant0/AndroidLiquidGlass)。

## 云端构建 APK(本机无需任何环境)

1. 在 GitHub 上新建一个**空仓库**(公开或私有均可)
2. 推送本目录:

   ```bash
   cd shiyin-android
   git init -b main
   git add -A
   git commit -m "拾音 Shiyin Android 初始版本"
   git remote add origin https://github.com/<你的用户名>/shiyin-android.git
   git push -u origin main
   ```

3. 打开仓库的 **Actions** 页,等待 "Build APK" 工作流跑完(首次约 5–10 分钟;
   构建失败时运行页顶部的 Summary 会自动列出编译错误)
4. 在该次运行详情页底部的 **Artifacts** 下载 `Shiyin-APK`,或到 **Releases** 页直接下载
   裸 APK(`Shiyin-release.apk`,免解压免登录)
5. 手机安装即可(允许安装未知来源应用)

> 首次构建会自动生成签名密钥 `keystore.jks` 并提交回仓库,保证后续每次构建签名一致,
> 可以直接覆盖安装、歌单数据不丢。私有仓库的 Actions 每月有免费额度(个人使用绰绰有余)。

## 限制

- 仅支持公开内容;部分版权/付费内容可能解析失败
- 定位精度为 IP 归属地级别(省 / 市),非 GPS 精确定位
- 仅供个人学习使用
