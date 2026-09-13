# 临时分享诊断（QQ 9.3.50 / 15730）

静态分析：ForwardArkMsgOption.r、ForwardArkH5StructOption.forwardOnConfirm/v、ForwardArkMsgForWebShareOption.realForwardTo/s，以及 ArkAppMessage.fromAppXml/toAppXml/toShareMsgJSONObject 是候选观察点。ForwardArkMsgOption 中可见 ChatActivityFacade.v(... ArkAppMessage, int) 调用；还存在其他分支。尚未确认实际音乐小程序走哪个分支，亦未找到 token 生成位置。

安装构建后重启 QQ。长按群聊右上角 → 临时分享诊断 → 开始记录。先从 QQ 内音乐小程序正常分享歌曲，再发送“点歌 晴天”和序号，最后进入诊断菜单停止并导出。日志位于 Download/ACE-Diagnostics/ACE-share-时间戳.log。

默认不记录；开始后当前进程记录 5 分钟，私有日志限制约 2 MiB，异步写入队列有上限。不会修改任何参数、结果或吞掉原始异常。安装时记录每个 Hook 成功/失败信息。覆盖当前 QQ 进程，其他进程需另行定位，不保证捕获所有分享。日志含分享歌曲、JSON结构、调用栈和参数摘要，不采集语音或二进制内容；账号和 token 用长度/指纹替代，URL去除查询参数。不会自动上传。停止后等待队列刷新再导出，导出失败保留私有日志可重试。

对比 ENTER/EXIT 同一调用编号，观察正常分享与点歌的路径、token指纹出现时机、Ark消息内容与发送参数差异。此版本用于诊断，不宣称已修复接收端不可见问题。
