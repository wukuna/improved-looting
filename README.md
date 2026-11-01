# Improved Looting

**This plugin is currently in Beta mode.**

Make ground loot easier to scan with compact cards or classic labels, Native / OSRS themes, custom backgrounds and seven configurable price tiers. Add loot beams, text timers or fading item pictures, hide unwanted drops, and browse nearby loot from the side panel.

Open the chest side panel for Display, Style, Rules and imports, and Alerts. Hover controls for brief explanations. Existing filters, assignments, assets and price tiers retain their saved settings.

**Availability:** Install **Improved Looting** from RuneLite's Plugin Hub. The left-click priority, ensouled-head, per-card width, strip timer and optional menu-hiding update [#19873](https://github.com/runelite/plugin-hub/pull/19873) merged on 1 November 2026. The follow-up [#19891](https://github.com/runelite/plugin-hub/pull/19891) includes Alt-click reliability, highlight colors and conditional menu hiding; these changes require the matching updated build.

**More plugins:** [Banking, Looting, Right-Clicks, Interface Layout and Chat](https://github.com/wukuna#runelite-plugins).

## What is included

- **Rules:** visual item search and icon palette; optional minimum stack value, color, value/timer, beam, tile highlight, notification and custom sound. Advanced RS2F scripts remain editable without conversion. Earlier terminating matches win; continuing apply blocks merge settings.
- **Nearby loot / inspector:** list or grid, name/category search, value/distance/time/name sorting and hidden-item access. Selecting a pile highlights its tile for ten seconds. The inspector shows facts, matching rule order/source lines, final settings and unknown information. Show, hide, highlight and reset overrides take precedence over the imported filter. Categories are saved per item.
- **Ground cards:** one row with a coloured left strip, picture, name, quantity in parentheses and price: `Law rune (2) (8,300 gp) [42s]`. Text uses the same tier/filter colour as the strip, including enabled colour effects. Each price tier has an independent bold switch under Edit seven price tiers. Short prices such as `(8.3k gp)` are optional. Choose Native (the original rounded cards) or OSRS (square bronze edges and RuneScape lettering) on Display, directly below the Cards / Labels selector. Under Style > Cards only, pick any background color, adjust opacity separately, or use the selected theme's default color. Custom colors survive theme changes and full setup backups. Background and thin borders can each be disabled. Pictures only shows the icon and quantity in both display styles. Prices and timers have explicit Hidden / Follow rules / Always controls; Hidden overrides rules without editing the filter.
- **Card sizing:** slim rows follow text size by default (18px rows with the default 12px font). Fit full item names automatically measures the complete line, including bold, quantity, price and timer. Turn it off to choose a manual width from 120–2,048px. Height can be automatic or customized up to 96px; it never clips the text vertically. Pictures scale with row height. Cards remain within the available game viewport, so unusually long lines can still shorten at the screen edge or when a narrower manual width is chosen. Existing manual width and classic-label image-size settings are preserved.
- **Uncluttered piles:** cards combine matching stacks on the same tile, sort by total value by default and show four rows before a hover-to-expand footer. Width and row limit are adjustable; nearby card blocks avoid one another and stay within the viewport. Normal clicks pass through. Alt-click edits label visibility without picking up items. The nearby browser remains available when all rows cannot fit.
- **Despawn display:** independent text-timer and top-to-bottom picture-fade switches allow either, both or neither. The fade measures the remaining lifetime when the pile is first seen; unknown/infinite expiry times leave the picture visible. Changing appearance does not reset an observed lifetime. Grouped cards use the earliest known expiry.
- **Classic labels:** retained as the Labels style, with the same pictures-only, price, timer, grouping, alignment, spacing, image enlargement and fade choices as cards. Rule-selected label borders/backgrounds remain separate from card surface options. Custom images retain their source dimensions and transparency; display scaling preserves aspect ratio. RuneLite beams use the item's world view, including sub-worlds.
- **Seven price tiers:** total-stack GE bands, solid colors, RGB, rainbow and directional waves, with per-tier custom sounds. Effects remain optional and do not rewrite menu colors.
- **Tier loot beams:** tick Beam for any tier under Edit seven price tiers and save. No filter rule or colour-effect enable switch is required; Ground display and the Loot beams master switch must be on. Tier beams use the tier's solid colour and the combined stack value shown on the card. Hidden items do not gain tier beams. Explicit rule beams and their colours still apply even when a tier checkbox is off. On a shared tile, the highest-value eligible stack supplies the single beam. Up to 64 nearby tiles can have beams, with value priority; disabling beams, despawns and world transitions remove old objects.
- **Alerts:** rule notifications, short-lived notification cards, custom WAV/AIFF/AU sounds, shared volume/cooldown, duplicate suppression and once-per-pile despawn warnings with a value threshold. Already-present piles stay silent on startup. Piles first seen near expiry do not immediately generate warnings.
- **Presets / full backup:** named complete setups, visible/valuable starter previews, copy/paste export/import and undo. Settings, RS2F source, embedded images/sounds, tiers, categories and overrides travel together. Validation finishes before settings are applied; stale profile/settings changes prevent overwrite.
- **Migration:** import RS2F files/assets through explicit file selection; copy an old Right-Clicks loot setup into an empty destination; or preview a copy of Ground Items highlight/hide name lists. The latter intentionally migrates those lists only, not arbitrary plugin settings. Original settings/files are never changed.
- **Wiki drop table:** enter a monster/activity to open its actual OSRS Wiki Drops section, then create a rule from a nearby item or use the item-search builder. This is an external browser workflow; no drop table is scraped or invented.
- **Source availability:** RuneLite 1.13.4 `ItemStack.getLocation()` always returns null. Monster loot records cannot identify an exact ground pile, so monster-source filtering is unavailable in this build. The inspector explains this; it never infers a monster from proximity.

## Getting started

Enable **Improved Looting** and open its chest side panel. Choose **Display**, enable **Show ground labels** and select **CARDS**. Cards are the default style; existing ground-display on/off settings are retained. **Text timer** and **Fade picture as loot expires** are independent controls. Choose Display, Style, Rules and imports, or Alerts using the page selector; style-specific controls disable when inapplicable.

Another active Ground Items / Loot Filters display now produces a visible overlap warning instead of silently suppressing our cards. Turn off the other display if you see duplicate labels. No other plugin's settings are changed. The nearby browser can still operate with our display off. Sounds, notifications, despawn warnings and price effects retain their saved switches and start disabled for new profiles.

**Display → Prioritize valuable loot** starts enabled. Top to bottom, each pile shows normal tradeable loot by total GE value (highest first), then bones/ashes, then untradeables. Bones/ashes are recognized by their item names; this does not attempt to identify which monster dropped an item. Cards, classic labels, the default left-click Take action and opened right-click Take rows use this order. Grouped quantities supply the stack value; owned-only grouping excludes other players' quantities. Turn it off to restore your saved Item order and filter menu priorities. The switch is included in backups and presets.

Only ordinary Take rows on the same tile and world view are reordered. Other actions, callbacks, submenus and existing deprioritized groups keep their positions. The highest-priority ordinary Take entry also becomes the default Take action before a click. Selected-item/spell targeting menus are left intact. No scene items are changed and no actions are invoked automatically; each click still performs one normal action.

**Connect menu icons** supplies resolved visibility, menu priority, color and independent images to a compatible Improved Right-Clicks version. Its existing icon and menu-compatibility switches control consumption. The priority above is shared with that plugin when enabled; otherwise the filter's menu sort value is retained. Native / Custom / Both and colors keep their independent controls. Turning the connection off allows its existing Loot Filters provider to be used. Neither plugin changes the other's settings.

## Bounds and limitations

Tracking is capped at 4,096 items. At most 256 fact/rule refreshes run in a game tick; dense scenes populate progressively. GE values refresh in staggered batches. The browser shows up to 512 nearby entries; labels default to 300 and can be capped between 10 and 1,000. Beams are capped at 64 tiles. Unknown despawn times are reported as unknown. Grouped labels use the earliest known expiry and combine only matching display decisions.

Custom images are limited to 2 MB / 2048 x 2048 each and 16 million decoded pixels combined per asset collection. Audio is limited to 15 seconds / 1 MB per file and 4 MB per collection. Presets are limited to twelve / 24 MB combined. At most five notification cards are visible; OS notifications are capped at three per game tick to avoid floods.

Tests cover rule resolution, user fixtures, silent seeding, despawn thresholds, grouping, overrides, migration guards, assets, menu protocol, late-image invalidation, production card rendering, hover expansion, independent timer modes and fade pixels. Dialogs are rendered and inspected with RuneLite's look and feel. Synthetic dense-scene timings are engineering checks, not live FPS measurements. Not every sound, sub-world beam or third-party configuration has been validated live; automated checks do not establish live activation.

No automatic pickup, gameplay actions, historical loot tracker, GPU depth prototype or interface positioning system is included. TFA exclusions remain unchanged. Imported parser code retains its BSD-2-Clause attribution in the packaged resources.

## Label visibility

Hold **Alt** to reveal hidden labels. Alt-click a visible label to hide every copy of that item; Alt-click a revealed hidden label to always show it again. These preferences persist in your RuneLite profile and full setup backups. They affect labels and automatic beams, leaving item menu actions and imported rules intact. Normal clicks pass through; an Alt-click used to edit a label is consumed and never picks up an item.

Showing an item again restores its existing appearance; it does not add a highlight. Existing Highlight overrides keep the item name, card color strip and beam yellow. Explicit rule text colors also take precedence over price-tier colors and animations, in both card themes and Labels mode. Hidden labels revealed with Alt remain gray. Visibility edits retain nearby rows without waiting for the next game tick; dragging a label does not toggle it.

**Display → Minimum value to appear (gp)** hides stacks below their total GE value. Zero disables the minimum. Grouping uses the combined same-tile stack value when enabled; manual Alt-click show overrides the minimum and rule hiding. Nearby loot includes hidden items, and its Filter action clears the manual label override.

**Keep untradeables visible** starts enabled and exempts untradeable items from the minimum, including drops with no GE value. **Untradeable color**, beside it on Display, defaults to red and can be changed with the picker. It colors the name and card accent in either theme and in Labels mode; existing non-white rule colors take priority. Price-tier animations do not replace the untradeable color. Explicit hide rules and Alt-click hiding still work.

**Only show owned drops** starts off. Enable it under Display > Visibility to show your drops and shared Group Ironman loot, based on RuneLite's ownership flags. Public/unowned and other-player drops are hidden, including their beams, notification cards, sounds and despawn warnings. Alt can temporarily reveal hidden ground labels; it does not reveal beams or trigger alerts. An explicit Show override does not bypass this ownership filter. Grouped quantities exclude drops hidden by ownership. All three settings are included in full backups and presets.

## Additional display controls

- **Style > Cards only > Fit each card separately** gives each row its own content width instead of matching the longest line in its pile. Off by default; applies with Fit full item names or Pictures only. Manual width still fixes every row to the chosen width.
- **Display > Color strip despawn timer** drains the left accent from top to bottom. It is independent of Text timer and Fade picture as loot expires, so use any combination. Unknown despawn times leave the strip full.
- **Display > Hide filtered items in menus** sets the default for hidden tags without an item or rule menu choice. It starts off. See conditional menu hiding below.
- Fresh ensouled heads use RuneLite's explicit tradeable-head mapping for their category and tier coloring, while retaining their actual ground-item identity and native pickup action.

## Conditional menu hiding

For an exact item, open **Display > Nearby loot / hidden labels**, select it, choose **Hidden tag in menu**, then **Save menu choice**:

- **Follow default:** use its matching menu rule, otherwise the global checkbox.
- **Hide when tag hidden:** remove its ordinary ground actions only while its tag is hidden.
- **Keep in menu:** retain those actions even when its tag is hidden.

For conditions, open **Display > Edit conditional menu rules**. Search an item name or use a wildcard, choose Hide/Keep, and optionally set minimum/maximum stack GE value, ownership or a map region. Leave **Menu choice only** selected to preserve existing appearance rules. Add the rule to the draft, check it, then save. The first matching explicit menu choice wins; new menu-only rules go before existing rules so a terminating appearance rule cannot prevent them being reached. Full appearance rules can also include a menu choice.

Precedence is **exact item choice > first matching menu rule > global checkbox**. Tags that are visible always keep their actions. The condition uses each native ground stack's quantity and value, not the total of separately grouped copies. Minimum/maximum values are inclusive; maximum 0 means no upper limit. Region matching uses the drop's location (instance template coordinates), on all planes, and does not match an unknown location.

Hold **Alt before opening the menu** to reveal actions. Only ordinary ground actions, including Take and Examine, are filtered. Item/spell targeting, custom callbacks, submenus and forced left-click entries are preserved. If duplicate copies disagree or any copy is unresolved, the menu entry remains available. These controls do not force another plugin to restore entries it has removed.

Item choices and rule conditions are included in presets, full backups and undo. Existing profiles inherit their current global setting; Alt-click and appearance edits retain separate item menu choices. Select Follow default and save to clear an exact-item choice; remove a conditional rule in the draft to clear it.

Advanced script extension (Improved Looting only): `hideMenu=true` means hide ordinary actions **when the tag is hidden**, `hideMenu=false` means keep them, and omitting it inherits the next default. For example, place `apply(name:"Bones" && gevalue:<=1000) { hideMenu=true; }` before existing appearance rules. Existing RS2F files remain unchanged unless you edit them; other RS2F consumers may not support this additional field.
