package com.improvedlooting.rs2f.lang;

import com.improvedlooting.rs2f.DisplayConfig;
import com.improvedlooting.rs2f.LootFilter;
import com.improvedlooting.rs2f.FilterRule;
import com.improvedlooting.rs2f.model.SoundProvider;
import com.improvedlooting.rs2f.model.BufferedImageProvider;
import com.improvedlooting.rs2f.ast.leaf.AccountTypeCondition;
import com.improvedlooting.rs2f.ast.AndCondition;
import com.improvedlooting.rs2f.ast.leaf.AreaCondition;
import com.improvedlooting.rs2f.model.Comparator;
import com.improvedlooting.rs2f.ast.leaf.ConstCondition;
import com.improvedlooting.rs2f.model.FontType;
import com.improvedlooting.rs2f.ast.leaf.ItemIdCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemGeTradeableCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemNameCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemNotedCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemOwnershipCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemQuantityCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemStackableCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemTradeableCondition;
import com.improvedlooting.rs2f.ast.leaf.ItemValueCondition;
import com.improvedlooting.rs2f.ast.LeafCondition;
import com.improvedlooting.rs2f.ast.NotCondition;
import com.improvedlooting.rs2f.ast.OrCondition;
import com.improvedlooting.rs2f.ast.Condition;
import com.improvedlooting.rs2f.model.TextAccent;
import com.improvedlooting.rs2f.model.ValueType;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.improvedlooting.rs2f.lang.Token.Type.APPLY;
import static com.improvedlooting.rs2f.lang.Token.Type.ASSIGN;
import static com.improvedlooting.rs2f.lang.Token.Type.BLOCK_END;
import static com.improvedlooting.rs2f.lang.Token.Type.BLOCK_START;
import static com.improvedlooting.rs2f.lang.Token.Type.COLON;
import static com.improvedlooting.rs2f.lang.Token.Type.COMMA;
import static com.improvedlooting.rs2f.lang.Token.Type.EXPR_END;
import static com.improvedlooting.rs2f.lang.Token.Type.EXPR_START;
import static com.improvedlooting.rs2f.lang.Token.Type.FALSE;
import static com.improvedlooting.rs2f.lang.Token.Type.IDENTIFIER;
import static com.improvedlooting.rs2f.lang.Token.Type.IF;
import static com.improvedlooting.rs2f.lang.Token.Type.LIST_END;
import static com.improvedlooting.rs2f.lang.Token.Type.LIST_START;
import static com.improvedlooting.rs2f.lang.Token.Type.LITERAL_INT;
import static com.improvedlooting.rs2f.lang.Token.Type.LITERAL_STRING;
import static com.improvedlooting.rs2f.lang.Token.Type.META;
import static com.improvedlooting.rs2f.lang.Token.Type.OP_AND;
import static com.improvedlooting.rs2f.lang.Token.Type.OP_NOT;
import static com.improvedlooting.rs2f.lang.Token.Type.OP_OR;
import static com.improvedlooting.rs2f.lang.Token.Type.RULE;
import static com.improvedlooting.rs2f.lang.Token.Type.STMT_END;
import static com.improvedlooting.rs2f.lang.Token.Type.TRUE;

@RequiredArgsConstructor
public class Parser {
    private final TokenStream tokens;

    private final LootFilter.Builder builder = LootFilter.builder();

    public LootFilter parse() throws ParseException {
        while (tokens.isNotEmpty()) {
            var tok = tokens.take();
            if (tok.is(META)) {
                parseMeta();
            } else if (tok.is(IF) || tok.is(RULE)) {
                parseRule(true, tok.getLocation().getLineNumber());
            } else if (tok.is(APPLY)) {
                parseRule(false, tok.getLocation().getLineNumber());
            } else {
                throw new ParseException("unexpected token", tok);
            }
        }
        return builder.build();
    }

    private void parseMeta() {
        var block = tokens.take(BLOCK_START, BLOCK_END);
        while (block.isNotEmpty()) {
            var tok = block.takeExpect(IDENTIFIER);
            block.takeExpect(ASSIGN);
            switch (tok.getValue()) {
                case "name":
                    builder.setName(block.takeExpectLiteral().expectString());
                    block.takeExpect(STMT_END);
                    break;
                case "description":
                    builder.setDescription(block.takeExpectLiteral().expectString());
                    block.takeExpect(STMT_END);
                    break;
                case "area":
                    block.takeExpect(LIST_START);
                    int x0 = block.takeExpectLiteral().expectInt(); block.takeExpect(COMMA);
                    int y0 = block.takeExpectLiteral().expectInt(); block.takeExpect(COMMA);
                    int z0 = block.takeExpectLiteral().expectInt(); block.takeExpect(COMMA);
                    int x1 = block.takeExpectLiteral().expectInt(); block.takeExpect(COMMA);
                    int y1 = block.takeExpectLiteral().expectInt(); block.takeExpect(COMMA);
                    int z1 = block.takeExpectLiteral().expectInt(); block.takeOptional(COMMA);
                    block.takeExpect(LIST_END);
                    block.takeExpect(STMT_END);
                    break;
                default:
                    throw new ParseException("unrecognized metavalue", tok);
            }
        }
    }

    private void parseRule(boolean isTerminal, int sourceLine) {
        var operators = new Stack<Token>();
        var rulesPostfix = new ArrayList<Condition>();
        tokens.walkExpression(EXPR_START, EXPR_END, it -> {
            if (it.is(EXPR_START)) {
                operators.push(it);
            } else if (it.is(EXPR_END)) {
                while (!operators.isEmpty() && !operators.peek().is(EXPR_START)) {
                    var op = operators.pop();
                    if (op.is(OP_AND)) {
                        rulesPostfix.add(new AndCondition(null));
                    } else if (op.is(OP_OR)) {
                        rulesPostfix.add(new OrCondition(null));
                    }
                }
                operators.pop();
                unwindUnary(operators, rulesPostfix);
            } else if (it.is(OP_AND)) {
                operators.push(it);
            } else if (it.is(OP_OR)) {
                while (!operators.isEmpty() && operators.peek().is(OP_AND)) {
                    operators.pop();
                    rulesPostfix.add(new AndCondition(null));
                }
                operators.push(it);
            } else if (it.is(OP_NOT)) {
                operators.push(it);
            } else if (it.is(TRUE) || it.is(FALSE)) {
                rulesPostfix.add(new ConstCondition(it.expectBoolean()));
                unwindUnary(operators, rulesPostfix);
            } else if (it.is(IDENTIFIER)) {
                rulesPostfix.add(parseRule(it));
                unwindUnary(operators, rulesPostfix);
            } else {
                throw new ParseException("unexpected token in expression", it);
            }
        });

        while (!operators.isEmpty()) {
            var op = operators.pop();
            if (op.is(OP_AND)) {
                rulesPostfix.add(new AndCondition(null));
            } else if (op.is(OP_OR)) {
                rulesPostfix.add(new OrCondition(null));
            } else if (op.is(OP_NOT)) {
                rulesPostfix.add(new NotCondition(null));
            }
        }

        tokens.takeExpect(BLOCK_START);
        var builder = DisplayConfig.builder();
        while (!tokens.peek().is(BLOCK_END)) {
            var property = tokens.peek();
            if (property.getValue().equals("icon")) {
                parseIcon(builder);
                continue;
            }

            var assign = parseAssignment();
            switch (assign[0].getValue()) {
                case "textColor":
                case "color":
                    builder.textColor(assign[1].expectColor()); break;
                case "backgroundColor":
                    builder.backgroundColor(assign[1].expectColor()); break;
                case "borderColor":
                    builder.borderColor(assign[1].expectColor()); break;
                case "hidden":
                    builder.hidden(assign[1].expectBoolean()); break;
                case "showLootbeam":
                case "showLootBeam":
                    builder.showLootbeam(assign[1].expectBoolean()); break;
                case "showValue":
                    builder.showValue(assign[1].expectBoolean()); break;
                case "showDespawn":
                    builder.showDespawn(assign[1].expectBoolean()); break;
                case "notify":
                    builder.notify(assign[1].expectBoolean()); break;
                case "textAccent":
                    builder.textAccent(TextAccent.fromOrdinal(assign[1].expectInt())); break;
                case "textAccentColor":
                    builder.textAccentColor(assign[1].expectColor()); break;
                case "lootbeamColor":
                case "lootBeamColor":
                    builder.lootbeamColor(assign[1].expectColor()); break;
                case "fontType":
                    builder.fontType(FontType.fromOrdinal(assign[1].expectInt())); break;
                case "menuTextColor":
                    builder.menuTextColor(assign[1].expectColor()); break;
                case "highlightTile":
                    builder.highlightTile(assign[1].expectBoolean()); break;
                case "tileStrokeColor":
                    builder.tileStrokeColor(assign[1].expectColor()); break;
                case "tileFillColor":
                    builder.tileFillColor(assign[1].expectColor()); break;
                case "hideOverlay":
                    builder.hideOverlay(assign[1].expectBoolean()); break;
                case "hideMenu":
                    builder.hideMenu(assign[1].expectBoolean()); break;
                case "sound":
                    builder.sound(SoundProvider.fromExpr(assign[1])); break;
                case "menuSort":
                    builder.menuSort(assign[1].expectInt()); break;
                default:
                    throw new ParseException("unexpected identifier in display config block", assign[0]);
            }
        }
        tokens.takeExpect(BLOCK_END);

        this.builder.addRule(new FilterRule(buildRule(rulesPostfix), builder.build(), isTerminal, sourceLine));
    }

    private void unwindUnary(Stack<Token> operators, ArrayList<Condition> postfix) {
        while (!operators.isEmpty() && operators.peek().is(OP_NOT)) {
            operators.pop();
            postfix.add(new NotCondition(null));
        }
    }

    private Condition parseRule(Token first) {
        tokens.takeExpect(COLON);
        switch (first.getValue()) {
            case "id":
                return parseItemIdRule();
            case "ownership":
                return parseItemOwnershipRule();
            case "name":
                return parseItemNameRule();
            case "quantity":
                return parseItemQuantityRule();
            case "value":
                return parseItemValueRule(ValueType.HIGHEST);
            case "gevalue":
                return parseItemValueRule(ValueType.GE);
            case "havalue":
                return parseItemValueRule(ValueType.HA);
            case "tradeable":
                return parseItemTradeableRule();
            case "geTradeable":
                return parseItemGeTradeableRule();
            case "stackable":
                return parseItemStackableRule();
            case "noted":
                return parseItemNotedRule();
            case "area":
                return parseAreaRule();
            case "accountType":
                return parseAccountTypeRule();
            default:
                throw new ParseException("unknown rule identifier", first);
        }
    }

    private ItemIdCondition parseItemIdRule() {
        if (tokens.peek().is(LITERAL_INT)) {
            return new ItemIdCondition(tokens.take().expectInt());
        } else if (tokens.peek().is(LIST_START)) {
            var block = tokens.take(LIST_START, LIST_END, true);
            return new ItemIdCondition(block.expectIntList());
        } else {
            throw new ParseException("parse item id: unexpected argument token", tokens.peek());
        }
    }

    private ItemOwnershipCondition parseItemOwnershipRule() {
        return new ItemOwnershipCondition(tokens.takeExpect(LITERAL_INT).expectInt());
    }

    private Condition parseItemNameRule() {
        if (tokens.peek().is(LITERAL_STRING)) {
            return new ItemNameCondition(tokens.take().expectString());
        } else if (tokens.peek().is(LIST_START)) {
            var block = tokens.take(LIST_START, LIST_END, true);
            return new ItemNameCondition(block.expectStringList());
        } else {
            throw new ParseException("parse item name: unexpected argument token", tokens.peek());
        }
    }

    private ItemQuantityCondition parseItemQuantityRule() {
        var op = tokens.take();
        var value = tokens.takeExpect(LITERAL_INT);
        return new ItemQuantityCondition(value.expectInt(), Comparator.fromToken(op));
    }

    private ItemValueCondition parseItemValueRule(ValueType valueType) {
        var op = tokens.take();
        var value = tokens.takeExpect(LITERAL_INT);
        return new ItemValueCondition(value.expectInt(), Comparator.fromToken(op), valueType);
    }

    private ItemTradeableCondition parseItemTradeableRule() {
        var op = tokens.take();
        return new ItemTradeableCondition((op.expectBoolean()));
    }

    private ItemGeTradeableCondition parseItemGeTradeableRule() {
        var op = tokens.take();
        return new ItemGeTradeableCondition((op.expectBoolean()));
    }

    private ItemStackableCondition parseItemStackableRule() {
        var op = tokens.take();
        return new ItemStackableCondition((op.expectBoolean()));
    }

    private ItemNotedCondition parseItemNotedRule() {
        var op = tokens.take();
        return new ItemNotedCondition((op.expectBoolean()));
    }

    private AreaCondition parseAreaRule() {
        var start = tokens.peek();
        var coords = tokens.take(LIST_START, LIST_END, true).expectIntList();
        if (coords.size() != 6) {
            throw new ParseException("incorrect list size for area argument", start);
        }
        return new AreaCondition(new WorldPoint(coords.get(0), coords.get(1), coords.get(2)),
                new WorldPoint(coords.get(3), coords.get(4), coords.get(5)));
    }

    private AccountTypeCondition parseAccountTypeRule() {
        var type = tokens.takeExpect(LITERAL_INT).expectInt();
        return new AccountTypeCondition(type);
    }

    private Condition buildRule(List<Condition> postfix) {
        return new com.improvedlooting.rs2f.ast.PostfixCondition(postfix);
    }

    private Token[] parseAssignment() {
        var ident = tokens.takeExpect(IDENTIFIER);
        tokens.takeExpect(ASSIGN);
        var value = tokens.takeExpectLiteral();
        tokens.takeExpect(STMT_END);
        return new Token[]{ident, value};
    }

    private void parseIcon(DisplayConfig.DisplayConfigBuilder builder) {
        tokens.takeExpect(IDENTIFIER);
        tokens.takeExpect(ASSIGN);
        var type = tokens.takeExpect(IDENTIFIER);
        var args = tokens.takeArgList();
        if (type.getValue().equals("Sprite")) {
            if (args.size() != 2) {
                throw new ParseException("incorrect arg length in icon Sprite() expr", type);
            }
            var spriteId = args.get(0).takeExpect(LITERAL_INT).expectInt();
            var index = args.get(1).takeExpect(LITERAL_INT).expectInt();
            builder.icon(new BufferedImageProvider.Sprite(spriteId, index));
        } else if (type.getValue().equals("Item")) {
            if (args.size() != 1) {
                throw new ParseException("incorrect arg length in icon Item() expr", type);
            }
            var itemId = args.get(0).takeExpect(LITERAL_INT).expectInt();
            builder.icon(new BufferedImageProvider.Item(itemId));
        } else if (type.getValue().equals("File")) {
            if (args.size() != 1) {
                throw new ParseException("incorrect arg length in icon File() expr", type);
            }
            var filename = args.get(0).take().expectString();
            builder.icon(new BufferedImageProvider.File(filename));
        } else if (type.getValue().equals("CurrentItem")) {
            if (!args.isEmpty()) {
                throw new ParseException("incorrect arg length in icon CurrentItem() expr", type);
            }
            builder.icon(new BufferedImageProvider.CurrentItem());
        } else {
            throw new ParseException("unrecognized icon type", type);
        }
        tokens.takeExpect(STMT_END);
    }
}
