package com.improvedlooting.rs2f.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TokenStream {
    private final List<Token> tokens;
    private int index = 0;

    public TokenStream(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Token> getTokens() {
        return new ArrayList<>(tokens.subList(index, tokens.size()));
    }

    public Token peek() {
        for (int i = index; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.isSemantic()) {
				return token;
			}
        }

        return null;
    }

    public Token take(boolean includeWhitespace) {
        for (int i = index; i < tokens.size(); i++) {
            var next = tokens.get(i);
            if (next.isSemantic() || includeWhitespace && next.isWhitespace()) {
                index = i + 1;
                return next;
            }
        }

        index = tokens.size();
        return null;
    }

    public Token take() {
        return take(false);
    }

    public Token takeExpect(Token.Type expect, boolean includeWhitespace) {
        if (tokens.isEmpty()) {
            throw new ParseException("unexpected end of token stream");
        }

        var first = take(includeWhitespace);
        if (!first.is(expect)) {
            throw new ParseException("unexpected non-" + expect + " token", first);
        }
        return first;
    }

    public Token takeExpect(Token.Type expect) {
        return takeExpect(expect, false);
    }

    public Token takeExpectLiteral() {
        var first = take();
        if (!first.is(Token.Type.LITERAL_INT)
                && !first.is(Token.Type.LITERAL_STRING)
                && !first.is(Token.Type.TRUE)
                && !first.is(Token.Type.FALSE)) {
            throw new ParseException("unexpected non-literal token", first);
        }
        return first;
    }

    public List<Token> takeLine() {
        var line = new ArrayList<Token>();
        int i;

        for (i = index; i < tokens.size(); ++i) {
            var next = tokens.get(i);
            if (next.is(Token.Type.NEWLINE)) {
                ++i;
                break;
            }
            line.add(next);
        }

        index = i;
        return line;
    }

    public void takeOptional(Token.Type type) {
        if (peek().is(type)) {
            take();
        }
    }

    public void walkExpression(Token.Type start, Token.Type end, Consumer<Token> consumer) {
        var state = new Stack<Token>();
        if (!peek().is(start)) {
            throw new ParseException("unexpected start of expression", peek());
        }

        while (isNotEmpty()) {
            var next = take();
            if (next.is(start)) {
                state.push(next);
            } else if (next.is(end)) {
                if (!state.isEmpty()) {
                    state.pop();
                } else {
                    throw new ParseException("unbalanced expression");
                }
            }

            consumer.accept(next);
            if (state.isEmpty()) {
                return;
            }
        }
        if (!state.isEmpty()) {
            throw new ParseException("unbalanced expression");
        }
    }

    public TokenStream take(Token.Type start, Token.Type end, boolean preserveEnclosing) {
        var inner = new ArrayList<Token>();
        walkExpression(start, end, inner::add);
        if (!preserveEnclosing) {
            inner.remove(0);
            inner.remove(inner.size() - 1);
        }
        return new TokenStream(inner);
    }

    public TokenStream take(Token.Type start, Token.Type end) {
        return take(start, end, false);
    }

    public List<TokenStream> takeArgList() {
        var args = new ArrayList<TokenStream>();
        var current = new ArrayList<Token>();
        var expr = take(Token.Type.EXPR_START, Token.Type.EXPR_END);
        while (expr.isNotEmpty()) {
            var head = expr.peek();
            if (head.is(Token.Type.COMMA)) {
                if (current.isEmpty()) {
                    throw new ParseException("empty argument");
                } else {
                    args.add(new TokenStream(current));
                    current = new ArrayList<>();
                    expr.take();
                }
            } else if (head.is(Token.Type.EXPR_START)) {
                var nestedExpr = expr.take(Token.Type.EXPR_START, Token.Type.EXPR_END, true);
                current.addAll(nestedExpr.getTokens());
            } else if (head.is(Token.Type.LIST_START)) {
                var nestedExpr = expr.take(Token.Type.LIST_START, Token.Type.LIST_END, true);
                current.addAll(nestedExpr.getTokens());
            } else {
                current.add(expr.take());
            }
        }

        if (!current.isEmpty()) {
            args.add(new TokenStream(current));
        }
        return args;
    }

    public List<String> expectStringList() {
        return expectList().stream()
                .map(Token::expectString)
                .collect(Collectors.toList());
    }

    public List<Integer> expectIntList() {
        return expectList().stream()
                .map(Token::expectInt)
                .collect(Collectors.toList());
    }

    public List<Token> expectList() {
        var tokens = new ArrayList<Token>();

        takeExpect(Token.Type.LIST_START);
        while (isNotEmpty()) {
            if (peek().is(Token.Type.LIST_END)) {
                return tokens;
            }

            tokens.add(take());
            if (peek().is(Token.Type.COMMA)) {
                take();
            } else if (peek().is(Token.Type.LIST_END)) {
                take();
                break;
            } else {
                throw new ParseException("unexpected token in list", peek());
            }
        }
        if (isNotEmpty()) {
            throw new ParseException("unterminated list");
        }

        return tokens;
    }

    public boolean isNotEmpty() {
        for (int i = index; i < tokens.size(); i++) {
            if (tokens.get(i).isSemantic()) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return tokens.stream().map(Token::getValue).collect(Collectors.joining(""));
    }
}