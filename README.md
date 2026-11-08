# SimplePlayer

极简的 Minecraft 游戏内音乐播放器，面向 **NeoForge 1.21.1 / Java 21**。

目标是「能好好听歌」：网易云音乐与哔哩哔哩两个音源，克制的界面，不做喧宾夺主的附加功能。

## 状态

播放内核、设置界面与播放器主界面已经可用：

- 音频引擎：OpenAL 输出，JLayer 解 MP3、JAAD 解 AAC，支持淡入淡出与队列切歌
- 网易云 / 哔哩哔哩：搜索、取流、扫码登录、每日推荐、账号歌单
- 封面缓存与封面取色：主界面主题色跟随当前封面平滑过渡
- MD3 播放器界面：搜索 / 每日推荐 / 我的歌单三个内容面板，底部播放队列与登录 Sheet，
  窄窗口自动切换为底部导航栏

## 构建

本项目**不提供本地构建指引**。所有构建均在 GitHub Actions 上完成：

- 推送到 `main` 或提交 PR → 自动构建，产物在 Actions 的 Artifacts 中
- 推送 `v*` 标签 → 自动构建并发布 GitHub Release（含 jar 与 SHA256SUMS.txt）

版本号在 `gradle.properties` 的 `mod_version` 中维护，发布时同步打 tag：

```bash
git tag v0.1.0 && git push origin v0.1.0
```

## 目录约定

- `src/main/java/com/simpleplayer`：模组源码
- 本地用于参考的上游项目（`Jade/`、`MuoniumPlayer-multiversion/`、`YesSteveModel/`）不纳入版本控制

## 许可证

本项目以 **CC BY-NC-SA 4.0**（署名-非商业性使用-相同方式共享 4.0 国际）发布，全文见 [LICENSE](LICENSE)。

- **非商业**：不得用于商业目的。
- **相同方式共享**：修改后的作品需以相同许可证分发。

### 第三方致谢

- 配置界面的视觉与交互设计、部分实现移植自 [Jade](https://github.com/Snownee/Jade)（作者 Snownee），同样以 CC BY-NC-SA 4.0 授权。
- 音源与播放相关实现参考 [MuoniumPlayer](https://github.com/zihe114514/MuoniumPlayer-multiversion)（Apache-2.0）。
- 界面设计参考 [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel)（Apache-2.0）。
