# 群聊点歌

QQ 9.3.50 (15730)：长按群聊右上角进入独立群聊菜单。QQ 设置入口不受影响。

- 点歌开关：关闭 / 仅自己 / 所有人，按账号及群保存。
- 音乐发送方式：默认音乐卡片；可切换为语音（本地 SILK）。改变设置后需要重新点歌。
- 发送“点歌 歌名”或“QQ点歌 歌名”，再发送搜索列表序号；可发送“取消点歌”清除当前结果。
- 语音只使用 QQ 音乐接口返回的可用地址，音源不可用会提示失败。
- 下载上限 32 MiB，音频上限 10 分钟；Android MediaCodec 解码为 PCM，转为 24 kHz 单声道后，通过宿主 SilkCodecWrapper 编码，写入腾讯 SILK 文件头与帧长度。
- 缓存位于 QQ 私有 cache/ace_music；下载文件转换后删除，SILK 保留供异步上传，后续任务清理超过 24 小时的缓存。
- 卡片采用用户提供的 QQ 音乐 SDK 分享样本：com.tencent.tuwen.lua / news、qqconnect.sdkshare、appid 100497308，点击打开官方歌曲页面。发送时优先经过 QQ 的 ArkAppMessage / ChatActivityFacade 原生路径，卡片发送不请求音源，语音仍需可用音源。
- uin 使用当前账号，ctime 使用当前时间；不复制或伪造样本的 token、msg_seq、hosteuin。跨客户端可见性仍需接收端验证，模板匹配不能替代宿主或服务端的分享校验。

## 验证

Java 源码编译检查通过。通过用户提供的 QQ APK 检查了 SilkCodecWrapper 与 IMsgUtilApi 接口。尚未实机验证 native 编码、音源可用性、卡片播放和语音上传。需分别测试自己/所有人、切换账号或关闭开关、不可用歌曲、长歌曲和发送失败场景。

## OIAPI 实验卡片
音乐卡片模式先获取可用音源，再请求 https://oiapi.net/API/QQMusicJSONArk（format/url/song/singer/cover/jump），校验 code=1 及 message 中的 app/view/meta 后原样通过 QQNT ArkElement 发送。不传 QQ 账号、Cookie；跳转指向官方歌曲页面。缺少音源、接口失败或发送异常时附歌曲链接；回执超时也不会重发卡片。发送回执不证明接收端可展示，需跨设备验证。
