package com.improvedlooting.rs2f.lang;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.improvedlooting.rs2f.util.CollectionUtil.append;
import static com.improvedlooting.rs2f.util.TextUtil.quote;

@RequiredArgsConstructor
public class Preprocessor {
    private final TokenStream tokens;
    private int expansionSteps;

    private final Map<String, Define> defines = new HashMap<>();
    private final List<Token> preproc = new ArrayList<>();

    public TokenStream preprocess() throws PreprocessException {
        while (tokens.isNotEmpty()) {
            var next = tokens.take(true);
            if (next.is(Token.Type.PREPROC_DEFINE)) {
                parseDefine();
            } else {
                preproc.add(next);
                if (!next.is(Token.Type.NEWLINE)) {
                    preproc.addAll(tokens.takeLine());
                    preproc.add(new Token(Token.Type.NEWLINE, "\n", Location.UNKNOWN));
                }
            }
        }

        return new TokenStream(expandDefines(new ArrayList<>(), new TokenStream(preproc)));
    }

    private void parseDefine() {
        var nameToken = tokens.takeExpect(Token.Type.IDENTIFIER);
        var name = nameToken.getValue();
        var params = tokens.peek().is(Token.Type.EXPR_START)
                ? parseDefineParams() : null;
        if (params != null && params.isEmpty()) {
            throw new PreprocessException("#define " + quote(name) + " has empty param list found at " + nameToken.getLocation().toString());
        }
        var line = tokens.takeLine();
        if (!line.isEmpty() && line.get(0).isWhitespace()) {
            line.remove(0);
        }
        defines.put(name, new Define(name, params, line));
    }

    private List<String> parseDefineParams() {
        var params = new ArrayList<String>();
        tokens.takeExpect(Token.Type.EXPR_START);
        while (tokens.isNotEmpty()) {
            var next = tokens.take();
            if (next.is(Token.Type.EXPR_END)) {
                return params;
            } else if (next.is(Token.Type.IDENTIFIER)) {
                params.add(next.getValue());
                tokens.takeOptional(Token.Type.COMMA);
            } else {
                throw new PreprocessException("unterminated define param list");
            }
        }
        throw new PreprocessException("unterminated define param list");
    }

    private List<Token> expandDefines(List<String> visited, TokenStream tokens) {
        if(visited.size()>64) throw new PreprocessException("Macro nesting exceeds 64");
        var postproc = new ArrayList<Token>();
        while (tokens.isNotEmpty()) {
            if(++expansionSteps>1000000) throw new PreprocessException("Macro expansion limit exceeded");
            var token = tokens.take(true);
            if (!visited.contains(token.getValue()) && token.is(Token.Type.IDENTIFIER) && defines.containsKey(token.getValue())) {
                var define = defines.get(token.getValue());
                if (define.isParameterized()) {
                    var args = tokens.takeArgList();
                    if (args.size() != define.params.size()) {
                        throw new PreprocessException(String.format("invalid macro expansion of %s (wanted %d args, got %d)",
                                define.name, define.params.size(), args.size()));
                    }
                    postproc.addAll(expandParameterizedDefine(append(visited, define.name), define, args, token.getLocation()));
                } else {
                    var defineTokens = define.value.stream().map(
                                    macroToken -> {

                                        var newLocation = token.getLocation().withMacroSourceLocation(macroToken.getLocation().withMacroName(define.name));

                                        return macroToken.withLocation(newLocation);
                                    })
                            .collect(Collectors.toList());
                    postproc.addAll(expandDefines(append(visited, define.name), new TokenStream(defineTokens)));
                }
            } else {
                postproc.add(token);
            }
        }
        return postproc;
    }

    private List<Token> expandParameterizedDefine(List<String> visited, Define define, List<TokenStream> args, Location macroInvocation) {
        var expanded = new ArrayList<Token>();
        for (var defineToken : define.value) {
            var token = defineToken.withLocation(macroInvocation.withMacroSourceLocation(defineToken.getLocation().withMacroName(define.name)));
            if (!token.is(Token.Type.IDENTIFIER) || token.getValue().equals(define.name)) {
                expanded.add(token);
                continue;
            }

            var paramIndex = -1;
            for (var i = 0; i < define.params.size(); ++i) {
                if (define.params.get(i).equals(token.getValue())) {
                    paramIndex = i;
                    break;
                }
            }
            if (paramIndex > -1) {
                var arg = args.get(paramIndex);
                expanded.addAll(arg.getTokens());
            } else {
                expanded.add(token);
            }
        }
        return expandDefines(visited, new TokenStream(expanded));
    }

    @AllArgsConstructor
    private static class Define {
        final String name;
        final List<String> params;
        final List<Token> value;

        boolean isParameterized() {
            return params != null;
        }
    }
}
