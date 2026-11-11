# 拾音 Shiyin · Android

B站音频搜索与播放器 —— 网页版 [bilimusic](../bilimusic) 的纯原生安卓版。
Kotlin + Jetpack Compose + Media3,无 WebView、无内嵌服务器,APK 单文件独立运行。

## 功能

- **搜索**:关键词搜视频 / UP主,分页加载;UP主详情页看全部作品、播放全部、一键存为歌单
- **粘贴解析**:BV/av 号、视频链接(含 `?p=N`)、b23.tv 短链、收藏夹链接或 ID;单曲入队,多P视频与收藏夹自动创建歌单
- **歌单**:新建 / 加入曲目(去重)/ 删除 / 导出 JSON;多P视频自动聚合为歌单;新歌单封面自动取自曲目
- **播放器**:队列扁平化管理、列表/单曲循环、进度拖拽、倍速(0.75–2x)、睡眠定时、音质三档(切换保留进度)
- **失败处理**:音源 403 自动重新解析重试(保留进度);单曲失败自动跳下一首,连续 3 次失败暂停
- **后台播放**:前台服务 + 系统媒体通知(锁屏/通知栏控制),音频焦点与耳机拔出暂停由 Media3 托管
- **界面**:网易云风格深色 UI,迷你播放条 + 全屏播放页(下滑关闭)+ 队列底部抽屉

## 架构

```
app/src/main/java/com/shiyin/player/
├── MainActivity.kt          # 入口,组装 Compose
├── data/
│   ├── Models.kt            # Track / Galbum / Settings / QueueState(kotlinx-serialization)
│   └── AppStore.kt          # 持久化(filesDir JSON),对应网页版 localStorage v2
├── bili/
│   └── BiliApi.kt           # B站接口层:搜索(buvid+412重试)/视频信息/playurl 解析/
│                            #   UP主作品(老接口+wbi 签名兜底)/收藏夹/b23.tv/粘贴解析
├── player/
│   ├── PlaybackService.kt   # Media3 MediaSessionService 前台服务
│   │                        #   ResolvingDataSource:占位 URI shiyin://track → 打开时解析直链 + Referer
│   └── PlayerBus.kt         # UI ↔ 服务桥,队列以 AppStore 为唯一真源
└── ui/
    ├── Ui.kt                # 应用骨架(底部 Tab/迷你条/全局弹层)+ 通用组件
    ├── Search.kt            # 搜索页 + 粘贴解析弹窗(状态在对象层,切 Tab 不丢)
    ├── Pages.kt             # 歌单 Tab / 歌单详情 / UP主详情
    └── Player.kt            # 全屏播放页(倍速/定时/音质)+ 队列抽屉 + 加入歌单弹窗
```

要点:

- **不需要本地服务器**:网页版的音频代理在原生层由 `ResolvingDataSource` 替代 ——
  MediaItem 用占位 URI,真正打开流时才调 playurl 拿直链,并给 CDN 请求附加 `Referer`
  (B站 CDN 的防盗链要求),403 时自动重解析。
- **数据迁移**:原生版与网页版数据互不相通(存储机制不同),歌单需重新添加或用导出 JSON 过渡。

## 云端构建 APK(本机无需任何环境)

1. 在 GitHub 上新建一个**空仓库**(如 `shiyin-android`,公开或私有均可)
2. 推送本目录:

   ```bash
   cd shiyin-android
   git init -b main
   git add -A
   git commit -m "拾音 Shiyin Android 初始版本"
   git remote add origin https://github.com/<你的用户名>/shiyin-android.git
   git push -u origin main
   ```

3. 打开仓库的 **Actions** 页,等待 "Build APK" 工作流跑完(首次约 5–10 分钟)
4. 在该次运行详情页底部的 **Artifacts** 下载 `Shiyin-APK`,解压得 `Shiyin-release.apk`
5. 手机安装即可(允许安装未知来源应用)

> 首次构建会自动生成签名密钥 `keystore.jks` 并提交回仓库,保证后续每次构建签名一致,
> 可以直接覆盖安装、歌单数据不丢。私有仓库的 Actions 每月有免费额度(个人使用绰绰有余)。

## 限制

- 仅支持公开内容;部分版权/付费内容可能解析失败
- 快捷键、桌面双栏布局为网页版桌面专属,App 端不适用
- 仅供个人学习使用
