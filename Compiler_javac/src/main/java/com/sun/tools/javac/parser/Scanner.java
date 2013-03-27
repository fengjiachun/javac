/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.parser;

import java.nio.*;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.*;


import static com.sun.tools.javac.parser.Token.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

/** The lexical analyzer maps an input stream consisting of
 *  ASCII characters and Unicode escapes into a token sequence.
 *  词法分析器将一个包含着ASCII字符和Unicode转义的输入流映射成一个token序列
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Scanner implements Lexer {

    private static boolean scannerDebug = true;

    /* Output variables; set by nextToken():
     */

    /** The token, set by nextToken().
     * 当前Token
     */
    private Token token;

    /** Allow hex floating-point literals.
     * 允许16进制浮点的字面量  例如:int i = 0x1f
     * JDK1.5或以上版本支持，参见com.sun.tools.javac.code.Source
     */
    private boolean allowHexFloats;

    /** Allow binary literals.
     * 允许二进制的字面量 例如:int i = 0b010101;
     * JDK1.7或以上版本支持，参见com.sun.tools.javac.code.Source
     */
    private boolean allowBinaryLiterals;

    /** Allow underscores in literals.
     * 允许字面量中出现下划线 例如：int i = 1_123_999_0;
     * JDK1.7或以上版本支持，参见com.sun.tools.javac.code.Source
     */
    private boolean allowUnderscoresInLiterals;

    /** The source language setting.
     *  源代码版本相关的一些设置，参见enum Source
     */
    private Source source;

    /** The token's position, 0-based offset from beginning of text.
     * 基于0的偏移，当前Token的开始位置
     */
    private int pos;

    /** Character position just after the last character of the token.
     * 当前Token的最后一个字符位置
     */
    private int endPos;

    /** The last character position of the previous token.
     * 前一个Token最后一个字符的位置
     */
    private int prevEndPos;

    /** The position where a lexical error occurred;
     */
    private int errPos = Position.NOPOS;

    /** The name of an identifier or token:
     * 当前Token的Name
     */
    private Name name;

    /** The radix of a numeric literal token.
     * 一个数字是多少进制的(二进制、十进制、十六进制等)
     */
    private int radix;

    /** Has a @deprecated been encountered in last doc comment?
     *  this needs to be reset by client.
     */
    protected boolean deprecatedFlag = false;

    /** A character buffer for literals.
     * 比如在按字符读"public"这个关键字，在读完'c'之前，前面的字符都放在这个缓冲区里
     */
    private char[] sbuf = new char[128];
    private int sp;     // 指向sbuf当前字符的指针

    /** The input buffer, index of next chacter to be read,
     *  index of one past last character in buffer.
     *  一个char数组，读入完整的一个java文件的源码
     */
    private char[] buf;
    private int bp;     // 指向buf当前字符的指针
    private int buflen; // buf的长度
    private int eofPos; // 指向buf结束字符的指针

    /** The current character.
     * 当前字符
     */
    private char ch;

    /** The buffer index of the last converted unicode character
     */
    private int unicodeConversionBp = -1;

    /** The log to be used for error reporting.
     */
    private final Log log;

    /** The name table. 编译器的名称表 */
    private final Names names;

    /** The keyword table.
     * key为Name value为Token
     */
    private final Keywords keywords;

    /** Common code for constructors. */
    private Scanner(ScannerFactory fac) {
        log = fac.log;
        names = fac.names;
        keywords = fac.keywords;
        source = fac.source;
        allowBinaryLiterals = source.allowBinaryLiterals();                 // 允许二进制的字面量 例如:int i = 0b010101;
        allowHexFloats = source.allowHexFloats();                           // 允许16进制浮点的字面量  例如:int i = 0x1f
        allowUnderscoresInLiterals = source.allowUnderscoresInLiterals();   // 允许字面量中出现下划线 例如：int i = 1_123_999_0;
    }

    private static final boolean hexFloatsWork = hexFloatsWork();           // 是否支持16进制浮点
    private static boolean hexFloatsWork() {
        try {
            Float.valueOf("0x1.0p1");
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Create a scanner from the input buffer.  buffer must implement
     *  array() and compact(), and remaining() must be less than limit().
     */
    protected Scanner(ScannerFactory fac, CharBuffer buffer) {
        // buffer.limit():此Buffer缓冲区的限制
        this(fac, JavacFileManager.toArray(buffer), buffer.limit());
    }

    /**
     * Create a scanner from the input array.  This method might
     * modify the array.  To avoid copying the input array, ensure
     * that {@code inputLength < input.length} or
     * {@code input[input.length -1]} is a white space character.
     *
     * @param fac the factory which created this Scanner
     * @param input the input, might be modified
     * @param inputLength the size of the input.
     * Must be positive and less than or equal to input.length.
     */
    protected Scanner(ScannerFactory fac, char[] input, int inputLength) {
        this(fac);
        eofPos = inputLength;
        if (inputLength == input.length) {
            if (input.length > 0 && Character.isWhitespace(input[input.length - 1])) {
                inputLength--;
            } else {
                char[] newInput = new char[inputLength + 1];
                System.arraycopy(input, 0, newInput, 0, input.length);
                input = newInput;
            }
        }
        buf = input;
        buflen = inputLength;
        buf[buflen] = EOI;
        bp = -1;
        scanChar();
    }

    /** Report an error at the given position using the provided arguments.
     */
    private void lexError(int pos, String key, Object... args) {
        log.error(pos, key, args);
        token = ERROR;
        errPos = pos;
    }

    /** Report an error at the current token position using the provided
     *  arguments.
     */
    private void lexError(String key, Object... args) {
        lexError(pos, key, args);
    }

    /** Convert an ASCII digit from its base (8, 10, or 16)
     *  to its value.
     */
    private int digit(int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            lexError(pos+1, "illegal.nonascii.digit");
            ch = "0123456789abcdef".charAt(result);
        }
        return result;
    }

    /** Convert unicode escape; bp points to initial '\' character
     *  (Spec 3.3).
     */
    private void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            bp++; ch = buf[bp];
            if (ch == 'u') {    // 处理如 "\u0001"...
                do {
                    bp++; ch = buf[bp];
                } while (ch == 'u');
                int limit = bp + 3;
                if (limit < buflen) {
                    int d = digit(16);
                    int code = d;
                    while (bp < limit && d >= 0) {
                        bp++; ch = buf[bp];
                        d = digit(16);
                        code = (code << 4) + d;
                    }
                    if (d >= 0) {
                        ch = (char)code;
                        unicodeConversionBp = bp;
                        return;
                    }
                }
                lexError(bp, "illegal.unicode.esc");
            } else {
                bp--;
                ch = '\\';
            }
        }
    }

    /** Read next character.
     * 读下一个字符
     */
    private void scanChar() {
        ch = buf[++bp];
        if (ch == '\\') {
            convertUnicode();
        }
    }

    /** Read next character in comment, skipping over double '\' characters.
     * 读取注释中的下一个字符，跳过 "\\"
     */
    private void scanCommentChar() {
        scanChar();
        if (ch == '\\') {
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                bp++;
            } else {
                convertUnicode();
            }
        }
    }

    /** Append a character to sbuf.
     * 构造、填充sbuf
     */
    private void putChar(char ch) {
        if (sp == sbuf.length) {
            char[] newsbuf = new char[sbuf.length * 2];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
        sbuf[sp++] = ch;
    }

    /** Read next character in character or string literal and copy into sbuf.
     */
    private void scanLitChar() {
        if (ch == '\\') {
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                bp++;
                putChar('\\');
                scanChar();
            } else {
                scanChar();
                switch (ch) {
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    char leadch = ch;
                    int oct = digit(8);
                    scanChar();
                    if ('0' <= ch && ch <= '7') {
                        oct = oct * 8 + digit(8);
                        scanChar();
                        if (leadch <= '3' && '0' <= ch && ch <= '7') {
                            oct = oct * 8 + digit(8);
                            scanChar();
                        }
                    }
                    putChar((char)oct);
                    break;
                case 'b':
                    putChar('\b'); scanChar(); break;
                case 't':
                    putChar('\t'); scanChar(); break;
                case 'n':
                    putChar('\n'); scanChar(); break;
                case 'f':
                    putChar('\f'); scanChar(); break;
                case 'r':
                    putChar('\r'); scanChar(); break;
                case '\'':
                    putChar('\''); scanChar(); break;
                case '\"':
                    putChar('\"'); scanChar(); break;
                case '\\':
                    putChar('\\'); scanChar(); break;
                default:
                    lexError(bp, "illegal.esc.char");
                }
            }
        } else if (bp != buflen) {
            putChar(ch); scanChar();
        }
    }

    /**
     * 扫描数字
     * @param digitRadix 进制
     */
    private void scanDigits(int digitRadix) {
        char saveCh;
        int savePos;
        do {
            if (ch != '_') {
                putChar(ch);
            } else {
                if (!allowUnderscoresInLiterals) {
                    lexError("unsupported.underscore.lit", source.name);
                    allowUnderscoresInLiterals = true;
                }
            }
            saveCh = ch;
            savePos = bp;
            scanChar();
        } while (digit(digitRadix) >= 0 || ch == '_');
        if (saveCh == '_')
            lexError(savePos, "illegal.underscore");
    }

    /** Read fractional part of hexadecimal floating point number.
     */
    private void scanHexExponentAndSuffix() {
        if (ch == 'p' || ch == 'P') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                if (!allowHexFloats) {
                    lexError("unsupported.fp.lit", source.name);
                    allowHexFloats = true;
                }
                else if (!hexFloatsWork)
                    lexError("unsupported.cross.fp.lit");
            } else
                lexError("malformed.fp.lit");
        } else {
            lexError("malformed.fp.lit");
        }
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /** Read fractional part of floating point number.
     */
    private void scanFraction() {
        skipIllegalUnderscores();
        if ('0' <= ch && ch <= '9') {
            scanDigits(10);
        }
        int sp1 = sp;
        if (ch == 'e' || ch == 'E') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                return;
            }
            lexError("malformed.fp.lit");
            sp = sp1;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanFractionAndSuffix() {
        this.radix = 10;
        scanFraction();
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanHexFractionAndSuffix(boolean seendigit) {
        this.radix = 16;
        Assert.check(ch == '.');
        putChar(ch);
        scanChar();
        skipIllegalUnderscores();
        if (digit(16) >= 0) {
            seendigit = true;
            scanDigits(16);
        }
        if (!seendigit)
            lexError("invalid.hex.number");
        else
            scanHexExponentAndSuffix();
    }

    private void skipIllegalUnderscores() {
        if (ch == '_') {
            lexError(bp, "illegal.underscore");
            while (ch == '_')
                scanChar();
        }
    }

    /** Read a number. 读取一个数字
     *  @param radix  The radix of the number; one of 2, j8, 10, 16.
     */
    private void scanNumber(int radix) {
        this.radix = radix;
        // for octal, allow base-10 digit in case it's a float literal
        int digitRadix = (radix == 8 ? 10 : radix);
        boolean seendigit = false;
        if (digit(digitRadix) >= 0) {
            seendigit = true;
            scanDigits(digitRadix);
        }
        if (radix == 16 && ch == '.') {
            scanHexFractionAndSuffix(seendigit);
        } else if (seendigit && radix == 16 && (ch == 'p' || ch == 'P')) {
            scanHexExponentAndSuffix();
        } else if (digitRadix == 10 && ch == '.') {
            putChar(ch);
            scanChar();
            scanFractionAndSuffix();
        } else if (digitRadix == 10 &&
                   (ch == 'e' || ch == 'E' ||
                    ch == 'f' || ch == 'F' ||
                    ch == 'd' || ch == 'D')) {
            scanFractionAndSuffix();
        } else {
            if (ch == 'l' || ch == 'L') {
                scanChar();
                token = LONGLITERAL;
            } else {
                token = INTLITERAL;
            }
        }
    }

    /** Read an identifier.
     *  读取一个完整的标示符，存入sbuf
     */
    private void scanIdent() {
        boolean isJavaIdentifierPart;   // 是否为java标示符的一部分
        char high;
        do {
            if (sp == sbuf.length)
                putChar(ch);
            else
                sbuf[sp++] = ch;
            // optimization, was: putChar(ch);

            scanChar();
            switch (ch) {
            case 'A': case 'B': case 'C': case 'D': case 'E':
            case 'F': case 'G': case 'H': case 'I': case 'J':
            case 'K': case 'L': case 'M': case 'N': case 'O':
            case 'P': case 'Q': case 'R': case 'S': case 'T':
            case 'U': case 'V': case 'W': case 'X': case 'Y':
            case 'Z':
            case 'a': case 'b': case 'c': case 'd': case 'e':
            case 'f': case 'g': case 'h': case 'i': case 'j':
            case 'k': case 'l': case 'm': case 'n': case 'o':
            case 'p': case 'q': case 'r': case 's': case 't':
            case 'u': case 'v': case 'w': case 'x': case 'y':
            case 'z':
            case '$': case '_':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
            case '\u0000': case '\u0001': case '\u0002': case '\u0003':
            case '\u0004': case '\u0005': case '\u0006': case '\u0007':
            case '\u0008': case '\u000E': case '\u000F': case '\u0010':
            case '\u0011': case '\u0012': case '\u0013': case '\u0014':
            case '\u0015': case '\u0016': case '\u0017':
            case '\u0018': case '\u0019': case '\u001B':
            case '\u007F':
                break;
            case '\u001A': // EOI is also a legal identifier part
                if (bp >= buflen) {
                    name = names.fromChars(sbuf, 0, sp);
                    token = keywords.key(name);
                    return;
                }
                break;
            default:
                if (ch < '\u0080') {
                    // all ASCII range chars already handled, above
                    // 所有ASCII范围内的字符已经处理完
                    isJavaIdentifierPart = false;
                } else {
                    high = scanSurrogates();
                    if (high != 0) {
                        if (sp == sbuf.length) {
                            putChar(high);
                        } else {
                            sbuf[sp++] = high;
                        }
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(
                            Character.toCodePoint(high, ch));
                    } else {
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(ch);
                    }
                }
                if (!isJavaIdentifierPart) {
                    name = names.fromChars(sbuf, 0, sp);    // SharedNameTable.hashes缓存着name
                    token = keywords.key(name);             // 一个token完成
                    return;                                 // 跳出死循环
                }
            }
        } while (true);
    }

    /** Are surrogates supported?
     */
    final static boolean surrogatesSupported = surrogatesSupported();
    private static boolean surrogatesSupported() {
        try {
            // 确定给出的 char 值是否为一个高代理项代码单元（也称为前导代理项代码单元）。
            // 这类值并不表示它们本身的字符，而被用来表示 UTF-16 编码中的增补字符。
            // 该方法返回 true 的条件是当且仅当
            //          ch >= '\uD800' && ch <= '\uDBFF'
            // 为 true。
            Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    /** Scan surrogate pairs.  If 'ch' is a high surrogate and
     *  the next character is a low surrogate, then put the low
     *  surrogate in 'ch', and return the high surrogate.
     *  otherwise, just return 0.
     */
    private char scanSurrogates() {
        if (surrogatesSupported && Character.isHighSurrogate(ch)) {
            char high = ch;

            scanChar();

            if (Character.isLowSurrogate(ch)) {
                return high;
            }

            ch = high;
        }

        return 0;
    }

    /** Return true if ch can be part of an operator.
     * 是一个操作符?
     */
    private boolean isSpecial(char ch) {
        switch (ch) {
        case '!': case '%': case '&': case '*': case '?':
        case '+': case '-': case ':': case '<': case '=':
        case '>': case '^': case '|': case '~':
        case '@':
            return true;
        default:
            return false;
        }
    }

    /** Read longest possible sequence of special characters and convert
     *  to token.
     */
    private void scanOperator() {
        while (true) {
            putChar(ch);
            Name newname = names.fromChars(sbuf, 0, sp);
            if (keywords.key(newname) == IDENTIFIER) {
                sp--;
                break;
            }
            name = newname;
            token = keywords.key(newname);
            scanChar();
            if (!isSpecial(ch)) break;
        }
    }

    /**
     * Scan a documention comment; determine if a deprecated tag is present.
     * Called once the initial /, * have been skipped, positioned at the second *
     * (which is treated as the beginning of the first line).
     * Stops positioned at the closing '/'.
     */
    @SuppressWarnings("fallthrough")
    private void scanDocComment() {
        boolean deprecatedPrefix = false;

        forEachLine:        // GoodCode 循环内只处理一行source，遇到换行就跳到forEachLine重新执行循环
        while (bp < buflen) {

            // Skip optional WhiteSpace at beginning of line 跳过一行开始处的WhiteSpace
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            // Skip optional consecutive Stars 跳过*
            while (bp < buflen && ch == '*') {
                scanCommentChar();
                if (ch == '/') {    // 注释结束     "*/"
                    return;
                }
            }

            // Skip optional WhiteSpace after Stars 跳过*后面的WhiteSpace
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            deprecatedPrefix = false;   // @deprecated
            // At beginning of line in the JavaDoc sense.
            if (bp < buflen && ch == '@' && !deprecatedFlag) {  // 处理JavaDoc中的@deprecated
                scanCommentChar();
                if (bp < buflen && ch == 'd') {
                    scanCommentChar();
                    if (bp < buflen && ch == 'e') {
                        scanCommentChar();
                        if (bp < buflen && ch == 'p') {
                            scanCommentChar();
                            if (bp < buflen && ch == 'r') {
                                scanCommentChar();
                                if (bp < buflen && ch == 'e') {
                                    scanCommentChar();
                                    if (bp < buflen && ch == 'c') {
                                        scanCommentChar();
                                        if (bp < buflen && ch == 'a') {
                                            scanCommentChar();
                                            if (bp < buflen && ch == 't') {
                                                scanCommentChar();
                                                if (bp < buflen && ch == 'e') {
                                                    scanCommentChar();
                                                    if (bp < buflen && ch == 'd') {
                                                        deprecatedPrefix = true;
                                                        scanCommentChar();
                                                    }}}}}}}}}}}
            if (deprecatedPrefix && bp < buflen) {
                if (Character.isWhitespace(ch)) {
                    deprecatedFlag = true;
                } else if (ch == '*') {
                    scanCommentChar();
                    if (ch == '/') {
                        deprecatedFlag = true;
                        return;
                    }
                }
            }

            // Skip rest of line
            while (bp < buflen) {
                switch (ch) {
                case '*':
                    scanCommentChar();
                    if (ch == '/') {
                        return;
                    }
                    break;
                case CR: // (Spec 3.4)  回车符
                    scanCommentChar();
                    if (ch != LF) {
                        continue forEachLine;
                    }
                    /* fall through to LF case */
                case LF: // (Spec 3.4)  换行符
                    scanCommentChar();
                    continue forEachLine;
                default:
                    scanCommentChar();
                }
            } // rest of line
        } // forEachLine
        return;
    }

    /** The value of a literal token, recorded as a string.
     *  For integers, leading 0x and 'l' suffixes are suppressed.
     */
    public String stringVal() {
        return new String(sbuf, 0, sp);
    }

    /** Read token.
     */
    public void nextToken() {

        try {
            // prevEndPos : 前一个Token最后一个字符的位置
            // endPos     : 当前Token的最后一个字符位置
            prevEndPos = endPos;
            sp = 0;     // 指向sbuf当前字符的指针，解析下一个Token的时候先清零

            while (true) {
                // pos : 基于0的偏移，当前Token的开始位置
                // bp  : 指向buf当前字符的指针
                pos = bp;
                switch (ch) {   // ch 当前字符
                // ========================================处理ch == ' ' || ch == '\t' || ch == FF===============================
                case ' ':       // (Spec 3.6) 空格
                case '\t':      // (Spec 3.6) 制表符
                case FF:        // (Spec 3.6) Form feed character.
                    do {        // 处理连续的WhiteSpace，然后调用processWhiteSpace输出日志
                        scanChar();
                    } while (ch == ' ' || ch == '\t' || ch == FF);
                    endPos = bp;            // endPos 当前...的最后一个字符位置
                    processWhiteSpace();
                    break;
                // ========================================处理ch == ' ' || ch == '\t' || ch == FF===============================
                case LF:        // (Spec 3.4) 换行符
                    scanChar();
                    endPos = bp;                // endPos 当前...的最后一个字符位置
                    processLineTerminator();
                    break;
                case CR:        // (Spec 3.4) 回车符
                    scanChar(); // 读下一个字符
                    if (ch == LF) {             // 如果相连两个字符是[\r\n]，也就是换行符
                        scanChar();             // 读下一个字符
                    }
                    endPos = bp;                // endPos 当前...的最后一个字符位置
                    processLineTerminator();    // 一个换行打印日志
                    break;
                case 'A': case 'B': case 'C': case 'D': case 'E':
                case 'F': case 'G': case 'H': case 'I': case 'J':
                case 'K': case 'L': case 'M': case 'N': case 'O':
                case 'P': case 'Q': case 'R': case 'S': case 'T':
                case 'U': case 'V': case 'W': case 'X': case 'Y':
                case 'Z':
                case 'a': case 'b': case 'c': case 'd': case 'e':
                case 'f': case 'g': case 'h': case 'i': case 'j':
                case 'k': case 'l': case 'm': case 'n': case 'o':
                case 'p': case 'q': case 'r': case 's': case 't':
                case 'u': case 'v': case 'w': case 'x': case 'y':
                case 'z':
                case '$': case '_':
                    scanIdent();                // 读取一个完整的标示符
                    return;                     // 跳出 While(true) 循环(先处理完finally语句块)
                case '0':
                    scanChar();
                    if (ch == 'x' || ch == 'X') {
                        scanChar();
                        skipIllegalUnderscores();
                        if (ch == '.') {
                            scanHexFractionAndSuffix(false);
                        } else if (digit(16) < 0) {
                            lexError("invalid.hex.number");
                        } else {
                            scanNumber(16);
                        }
                    } else if (ch == 'b' || ch == 'B') {
                        if (!allowBinaryLiterals) {
                            lexError("unsupported.binary.lit", source.name);
                            allowBinaryLiterals = true;
                        }
                        scanChar();
                        skipIllegalUnderscores();
                        if (digit(2) < 0) {
                            lexError("invalid.binary.number");
                        } else {
                            scanNumber(2);
                        }
                    } else {
                        putChar('0');
                        if (ch == '_') {
                            int savePos = bp;
                            do {
                                scanChar();
                            } while (ch == '_');
                            if (digit(10) < 0) {
                                lexError(savePos, "illegal.underscore");
                            }
                        }
                        scanNumber(8);
                    }
                    return;
                case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    scanNumber(10);
                    return;
                case '.':
                    scanChar();
                    if ('0' <= ch && ch <= '9') {   // 浮点数
                        putChar('.');
                        scanFractionAndSuffix();
                    } else if (ch == '.') {
                        putChar('.'); putChar('.');
                        scanChar();
                        if (ch == '.') {            // ELLIPSIS("...") java方法中的变参
                            scanChar();
                            putChar('.');
                            token = ELLIPSIS;
                        } else {
                            lexError("malformed.fp.lit");
                        }
                    } else {
                        token = DOT;        // DOT(".")
                    }
                    return;
                case ',':
                    scanChar(); token = COMMA;      return;
                case ';':
                    scanChar(); token = SEMI;       return;
                case '(':
                    scanChar(); token = LPAREN;     return;
                case ')':
                    scanChar(); token = RPAREN;     return;
                case '[':
                    scanChar(); token = LBRACKET;   return;
                case ']':
                    scanChar(); token = RBRACKET;   return;
                case '{':
                    scanChar(); token = LBRACE;     return;
                case '}':
                    scanChar(); token = RBRACE;     return;
                case '/':
                    scanChar();
                    if (ch == '/') {        // 处理注释：    "//"
                        do {
                            scanCommentChar();
                        } while (ch != CR && ch != LF && bp < buflen);
                        if (bp < buflen) {
                            endPos = bp;    // endPos 当前...的最后一个字符位置
                            processComment(CommentStyle.LINE);
                        }
                        break;
                    } else if (ch == '*') { // 处理注释：    "/*"
                        scanChar();
                        CommentStyle style;
                        if (ch == '*') {    // 处理JavaDoc： "/**"
                            style = CommentStyle.JAVADOC;
                            scanDocComment();
                        } else {
                            style = CommentStyle.BLOCK;
                            while (bp < buflen) {
                                if (ch == '*') {
                                    scanChar();
                                    if (ch == '/') break;
                                } else {
                                    scanCommentChar();
                                }
                            }
                        }
                        if (ch == '/') {    // 注释(/** */或/* */)的结尾：   "/"
                            scanChar();
                            endPos = bp;    // endPos 当前...的最后一个字符位置
                            processComment(style);
                            break;
                        } else {
                            lexError("unclosed.comment");
                            return;
                        }
                    } else if (ch == '=') { // "/=" 运算符
                        name = names.slashequals;
                        token = SLASHEQ;
                        scanChar();
                    } else {                // "/" 除号
                        name = names.slash;
                        token = SLASH;
                    }
                    return;
                case '\'':
                    scanChar();
                    if (ch == '\'') {
                        lexError("empty.char.lit");
                    } else {
                        if (ch == CR || ch == LF)
                            lexError(pos, "illegal.line.end.in.char.lit");
                        scanLitChar();
                        if (ch == '\'') {
                            scanChar();
                            token = CHARLITERAL;
                        } else {
                            lexError(pos, "unclosed.char.lit");
                        }
                    }
                    return;
                case '\"':
                    scanChar();
                    while (ch != '\"' && ch != CR && ch != LF && bp < buflen)
                        scanLitChar();
                    if (ch == '\"') {
                        token = STRINGLITERAL;
                        scanChar();
                    } else {
                        lexError(pos, "unclosed.str.lit");
                    }
                    return;
                default:
                    if (isSpecial(ch)) {    // 是一个操作符?
                        scanOperator();
                    } else {
                        boolean isJavaIdentifierStart;
                        if (ch < '\u0080') {
                            // all ASCII range chars already handled, above
                            isJavaIdentifierStart = false;
                        } else {
                            char high = scanSurrogates();
                            if (high != 0) {
                                if (sp == sbuf.length) {
                                    putChar(high);
                                } else {
                                    sbuf[sp++] = high;
                                }

                                isJavaIdentifierStart = Character.isJavaIdentifierStart(
                                    Character.toCodePoint(high, ch));
                            } else {
                                isJavaIdentifierStart = Character.isJavaIdentifierStart(ch);
                            }
                        }
                        if (isJavaIdentifierStart) {
                            scanIdent();
                        } else if (bp == buflen || ch == EOI && bp+1 == buflen) { // JLS 3.5
                            token = EOF;
                            pos = bp = eofPos;
                        } else {
                            lexError("illegal.char", String.valueOf((int)ch));
                            scanChar();
                        }
                    }
                    return;
                }
            }
        } finally {
            endPos = bp;        // endPos 当前...的最后一个字符位置
            if (scannerDebug)
                System.out.println("nextToken(" + pos + "," + endPos + ")=|" + new String(getRawCharacters(pos, endPos)) + "|");
        }
    }

    /** Return the current token, set by nextToken().
     */
    public Token token() {
        return token;
    }

    /** Sets the current token.
     */
    public void token(Token token) {
        this.token = token;
    }

    /** Return the current token's position: a 0-based
     *  offset from beginning of the raw input stream
     *  (before unicode translation)
     *  基于0的偏移，当前Token的开始位置
     */
    public int pos() {
        return pos;
    }

    /** Return the last character position of the current token.
     * endPos 当前...的最后一个字符位置
     */
    public int endPos() {
        return endPos;
    }

    /** Return the last character position of the previous token.
     * 前一个Token最后一个字符的位置
     */
    public int prevEndPos() {
        return prevEndPos;
    }

    /** Return the position where a lexical error occurred;
     */
    public int errPos() {
        return errPos;
    }

    /** Set the position where a lexical error occurred;
     */
    public void errPos(int pos) {
        errPos = pos;
    }

    /** Return the name of an identifier or token for the current token.
     */
    public Name name() {
        return name;
    }

    /** Return the radix of a numeric literal token.
     */
    public int radix() {
        return radix;
    }

    /** Has a @deprecated been encountered in last doc comment?
     *  This needs to be reset by client with resetDeprecatedFlag.
     */
    public boolean deprecatedFlag() {
        return deprecatedFlag;
    }

    public void resetDeprecatedFlag() {
        deprecatedFlag = false;
    }

    /**
     * Returns the documentation string of the current token.
     */
    public String docComment() {
        return null;
    }

    /**
     * Returns a copy of the input buffer, up to its inputLength.
     * Unicode escape sequences are not translated.
     */
    public char[] getRawCharacters() {
        char[] chars = new char[buflen];
        System.arraycopy(buf, 0, chars, 0, buflen);
        return chars;
    }

    /**
     * 返回buf(存放源文件)中指定的一段字符数组
     * Returns a copy of a character array subset of the input buffer.
     * The returned array begins at the <code>beginIndex</code> and
     * extends to the character at index <code>endIndex - 1</code>.
     * Thus the length of the substring is <code>endIndex-beginIndex</code>.
     * This behavior is like
     * <code>String.substring(beginIndex, endIndex)</code>.
     * Unicode escape sequences are not translated.
     *
     * @param beginIndex the beginning index, inclusive.
     * @param endIndex the ending index, exclusive.
     * @throws IndexOutOfBounds if either offset is outside of the
     *         array bounds
     */
    public char[] getRawCharacters(int beginIndex, int endIndex) {
        int length = endIndex - beginIndex;
        char[] chars = new char[length];
        System.arraycopy(buf, beginIndex, chars, 0, length);
        return chars;
    }

    public enum CommentStyle {
        LINE,       // 单行注释                          "//"
        BLOCK,      // 注释块（跨行）           "/*    */"
        JAVADOC,    // javadoc          "/**   */"
    }

    /**
     * Called when a complete comment has been scanned. pos and endPos
     * will mark the comment boundary.
     */
    protected void processComment(CommentStyle style) {
        if (scannerDebug)
            System.out.println("processComment(" + pos
                               + "," + endPos + "," + style + ")=|"
                               + new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     */
    protected void processWhiteSpace() {
        if (scannerDebug)
            System.out.println("processWhitespace(" + pos
                               + "," + endPos + ")=|" +
                               new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a line terminator has been processed.换行时这个方法被调用
     */
    protected void processLineTerminator() {
        if (scannerDebug)
            System.out.println("processTerminator(" + pos
                               + "," + endPos + ")=|" +
                               new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /** Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap */
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(buf, buflen, false);
    }

}
