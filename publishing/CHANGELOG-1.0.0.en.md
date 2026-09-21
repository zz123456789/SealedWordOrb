# 1.0.0

- Sealed orbs are now single-use in both survival and creative mode.
- Consume the original orb before executing its command so rewards placed into the vacated slot are not overwritten or deleted.
- Prevent vanilla creative-mode use from restoring the consumed orb.
- Preserve the default state until a successful save writes the Modified marker; sealed data remains read-only in the normal editor.
- Keep portable NBT compatible with existing orbs and item-based quest rewards.
- Add coverage for air, block and entity use, both hands, failed commands, replacement items, and reward-template serialization.
- All 17 Minecraft server integration tests passed.

**Upgrade note:** orbs created in 0.1.0 remain valid and become single-use after upgrading. A command attempt consumes the orb even if the command fails; malformed sealed NBT does not execute or consume it.

Version numbering has been corrected: the current implementation is 1.0.0, and the original reusable implementation is 0.1.0. Builds previously labelled 1.1.0 and 1.0.0 during development correspond to these versions respectively.
