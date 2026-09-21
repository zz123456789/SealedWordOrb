# FTB Quests 奖励兼容性与整合包分发

## 核对结论

缄言珠 1.0.0 可以作为 Minecraft 1.20.1 的 FTB Quests **物品奖励**。已封存珠子的命令、权限等级、描述和只读标记都放在普通物品 NBT 中；没有世界密钥、创建者 UUID、服务器地址或仅保存在作者电脑上的配套数据。更换玩家或新建存档不会单独导致失效。

核对依据是 FTB Quests 官方 `1.20.1/main` 分支，固定提交 `a74ee04bf80e7fba2c8475112a07ace4a4abd0a6`：

- [ItemReward.java](https://github.com/FTBTeam/FTB-Quests/blob/a74ee04bf80e7fba2c8475112a07ace4a4abd0a6/common/src/main/java/dev/ftb/mods/ftbquests/quest/reward/ItemReward.java)：奖励通过 NBTUtils 存取物品；领取时用副本按最大堆叠数量分发，不消耗奖励模板。
- [NBTUtils.java](https://github.com/FTBTeam/FTB-Quests/blob/a74ee04bf80e7fba2c8475112a07ace4a4abd0a6/common/src/main/java/dev/ftb/mods/ftbquests/util/NBTUtils.java)：带附加 NBT 的物品使用完整 compound 保存，不会被简化为只有物品 ID 的字符串。
- [MissingItem.java](https://github.com/FTBTeam/FTB-Quests/blob/a74ee04bf80e7fba2c8475112a07ace4a4abd0a6/common/src/main/java/dev/ftb/mods/ftbquests/item/MissingItem.java)：使用 ItemStack.save/of 存取物品，保留 tag；Count 在任务配置中写为整数。
- [ServerQuestFile.java](https://github.com/FTBTeam/FTB-Quests/blob/a74ee04bf80e7fba2c8475112a07ace4a4abd0a6/common/src/main/java/dev/ftb/mods/ftbquests/quest/ServerQuestFile.java)：从 `config/ftbquests/quests/` 读取任务定义。

当前环境没有安装 FTB Quests。已经做源码核对及同格式 SNBT 导出/重读/副本发放测试，并测试新玩家、另一维度的非 OP 生存玩家可执行；**没有声称已实测某个具体 FTB 发行版的领取界面，也没有运行完整整合包。**

## 默认状态与只读状态

| 状态 | 判断条件 | 右键行为 | 是否消耗 |
| --- | --- | --- | --- |
| 默认/空白 | 没有 `SealedWordOrb.Modified` | 创造模式且至少 2 级管理员可打开编辑器 | 否 |
| 只读/封存 | 存在 `SealedWordOrb.Modified`，内容合法 | 以使用者身份和保存的权限尝试执行命令 | 一次一颗，创造模式也消耗 |
| 封存数据损坏 | 有标记，但命令/权限/描述缺失或不合法 | 报错，不执行，也不会退回可编辑状态 | 否 |

创建、取出空白珠或打开/取消编辑器不会写入 Modified。仅成功保存写入 `Modified:1b`。按原始要求，判定的是标记是否存在，`Modified:0b` 也算只读。

一颗珠子执行失败同样算使用一次。实现先消费原珠再执行命令，避免命令先清空/替换手中物品后又错误消费新物品，也避免原版创造模式恢复数量。

## 正确制作奖励

1. 创造模式管理员拿一颗缄言珠，填写并保存，例如 `give @s minecraft:diamond 3`、权限 `2`、描述“使用后获得三颗钻石”。
2. 在 FTB 任务编辑器中，将背包里的这颗**已封存珠**作为物品奖励，数量通常设为 1。若直接搜索物品 ID，选出来的可能只是空白默认珠。
3. 保存任务定义，核对导出的奖励内容中包含完整 `tag`。下面是奖励对象的物品内容示意，嵌入现有奖励即可；不是完整章节文件，也不要用它覆盖现有章节的 ID 等字段。

```snbt
type: "item"
item: {
    id: "sealedwordorb:sealed_word_orb"
    Count: 1
    tag: {
        SealedWordOrb: {
            Command: "give @s minecraft:diamond 3"
            PermissionLevel: 2
            Description: "使用后获得三颗钻石"
            Modified: 1b
        }
    }
}
count: 1
```

4. 将 `config/ftbquests/quests/` 的任务定义随整合包一起分发，服务端也使用对应任务定义。玩家个人任务完成进度不应当作任务定义替代品。
5. 客户端和服务端都安装缄言珠 1.0.0；只保留一个版本。整合包也需包含相容的 FTB Quests 及其依赖，以及命令所依赖的其他模组、脚本或数据包。

同一奖励模板可给不同玩家发放，每次发放的是独立珠子。消耗领取的珠子不会修改任务配置里的模板；可重复任务是否允许重复领奖由 FTB 的任务设置决定。

## 分发后可能失效的具体原因

| 原因 | 表现/处理 |
| --- | --- |
| 奖励只保存物品 ID，没保存 tag | 领取到空白珠；普通玩家不能编辑。改用完整的封存物品作为奖励。 |
| 没有把任务定义一起打包 | 新安装看不到对应任务或奖励。核对 config/ftbquests/quests。 |
| 缺少缄言珠模组、版本不匹配，或客户端/服务端不一致 | 缺失物品、连接失败或不能正确使用。使用相同的 1.0.0。 |
| 命令引用作者名字/UUID、旧存档实体、记分板、坐标或自定义维度 | 新玩家/新世界中目标不存在。优先使用 @s，相对坐标也要符合实际场景；需要的记分板/维度/数据包须随整合包配置。 |
| 命令来自没打包的模组、KubeJS 脚本或 function 数据包 | 命令无法识别或功能不存在；需包含对应依赖和初始化步骤。 |
| 保存权限不足或外部权限插件另有限制 | 原版命令报错；测试实际需要的 0–4 权限。更改服务器 op-permission-level 不会自动改写已保存等级。 |
| 将物品的 tag 错写在奖励根节点，或把 PermissionLevel 写成字符串 | 变成空白或封存数据无效。按导出格式保存正确层级与类型。 |

建议发布前在整合包的干净新存档中，以非 OP 生存玩家实际领取一次：检查说明、右键效果、消耗和重新登录后的持久化。这是验证整合包自身命令依赖与具体 FTB 版本的最后一步，当前源码兼容性检查不能代替它。

纯 NBT 伪造风险仍按之前选择保留；此兼容性设计不会加入跨存档签名绑定。
