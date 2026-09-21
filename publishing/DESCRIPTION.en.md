# Sealed Word Orb

**Inscribe a command. Seal it into an item. Let its holder use it once.**

Sealed Word Orb (缄言珠) adds a configurable command item for Minecraft Java Edition **1.20.1 / Forge**. It is designed for adventure maps, server events, and modpack quest rewards: an administrator prepares an orb, then a player right-clicks it to run the stored command.

## Features

- A creative-mode item with its own texture and creative inventory tab.
- A simple editor with three fields: command, permission level, and optional description.
- Blank commands cannot be saved. A blank permission field defaults to **level 2**; a blank description adds no description text.
- Saving permanently seals the orb against further editing through its interface.
- In **1.0.0**, each sealed orb is **single-use**. Using it consumes one orb, including in creative mode.
- Works from either hand when right-clicking air, blocks, or entities.
- English and Simplified Chinese interface translations.

## How to use

1. Enter creative mode with operator permission level 2 or higher.
2. Find **Sealed Word Orb** in its creative tab, or run `/give @s sealedwordorb:sealed_word_orb`.
3. Right-click an unsealed orb. Enter a command such as `give @s minecraft:diamond 3`, choose a permission level, and optionally add a description.
4. Select **Save and seal**. Canceling leaves the orb unchanged.
5. Give the sealed orb to a player. Right-clicking it attempts the command and consumes the orb.

Permission levels range from **0 to 4**. An editor cannot assign a level higher than their own. Sealed orbs can be used by non-operator survival players: execution uses the stored permission level while retaining the current user's identity, position, rotation, and dimension. Therefore, `@s` refers to the player using the orb.

**A command attempt spends the orb even if the command fails.** Invalid or incomplete sealed NBT is rejected without executing or consuming the item. Commands are not syntax-checked when saved; test your intended command before distributing a reward.

## Quest rewards and modpacks

The command, permission, description, and sealed marker are stored in ordinary item NBT. Orbs are not bound to their creator or to a particular world.

For an **FTB Quests item reward**, select an already sealed orb and retain its full NBT. Choosing only the item ID creates a blank orb. Distribute the quest definitions and every mod, script, or data pack required by the command with your pack. Prefer `@s` over a hard-coded player name.

FTB Quests is **not a required dependency**. Its Minecraft 1.20.1 reward serialization has been reviewed, and matching NBT export/import behavior has been tested. A complete FTB Quests modpack playthrough has not been performed.

## Installation

- Minecraft Java Edition **1.20.1**
- **Forge 47.4.10 or a compatible later 47.x release**
- **Java 17**
- Install the mod on **both the client and the server** for multiplayer.

Only install one version of Sealed Word Orb at a time.

## Security and intended use

This mod intentionally uses **unsigned, portable NBT**. The normal editor is restricted to creative operators, but a modified creative client or external NBT editor can forge or change an orb's command and permission level. The sealed marker is an interface lock, not tamper-proof protection. Use it in trusted environments and do not treat it as a security boundary against untrusted creative players.

Other mods or server permission systems may impose additional checks beyond vanilla command levels.

## License

Sealed Word Orb's original code, assets, and documentation are available under the **MIT License**, including the previously released 0.1.0 and 1.0.0 versions. You may use, modify, and redistribute them, including in modpacks, under the MIT terms. Third-party components retain their own licenses.

[Source code](https://github.com/zz123456789/SealedWordOrb) · [MIT license](https://github.com/zz123456789/SealedWordOrb/blob/main/LICENSE.md) · [Historical-release clarification](https://github.com/zz123456789/SealedWordOrb/blob/main/LICENSING.md)

## Version history

- **1.0.0 — Current release:** single-use sealed orbs, including creative-mode consumption; preserves command-generated replacement items. Existing NBT remains compatible.
- **0.1.0 — Historical release:** the original editor and sealed-command implementation; sealed orbs are reusable in this version.

The latest release has passed **17 Minecraft server integration tests** covering NBT, permissions, interaction handling, single-use behavior, and serialized reward copies. This does not replace testing the specific commands and mod combinations in your pack.
