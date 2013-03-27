/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.main;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

/**
 * TODO: describe com.sun.tools.javac.main.JavacOption
 * javac那些命令行选项，分为：
 *      1. 标准选项
 *      2. 非标准选项
 *      3. 隐式选项
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public interface JavacOption {

    OptionKind getKind();

    /** Does this option take a (separate) operand?
     *  @return true if this option takes a separate operand
     */
    boolean hasArg();

    /** Does argument string match option pattern?
     *  @param arg   the command line argument string
     *  @return true if {@code arg} matches this option
     */
    boolean matches(String arg);

    /** Process an option with an argument.
     *  @param options the accumulated set of analyzed options
     *  @param option  the option to be processed
     *  @param arg     the arg for the option to be processed
     *  @return true if an error was detected
     */
    boolean process(Options options, String option, String arg);

    /** Process the option with no argument.
     *  @param options the accumulated set of analyzed options
     *  @param option  the option to be processed
     *  @return true if an error was detected
     */
    boolean process(Options options, String option);

    OptionName getName();

    enum OptionKind {
        NORMAL,         // 标准选项
        EXTENDED,       // 非标准选项(也叫扩展选项，用"-X"来查看所有非标准选项)
        HIDDEN,         // 隐式选项(内部使用，不会显示)
    }

    enum ChoiceKind {
        ONEOF,
        ANYOF
    }

    /** This class represents an option recognized by the main program
     * 标准选项
     */
    static class Option implements JavacOption {

        /** Option string.
         */
        OptionName name;

        /** Documentation key for arguments.
         */
        String argsNameKey;

        /** Documentation key for description.
         */
        String descrKey;

        /** Suffix option (-foo=bar or -foo:bar)
         * 有后缀，选项名称最后一个字符是 "=" 或 ":"
         */
        boolean hasSuffix;

        /** The kind of choices for this option, if any.
         */
        ChoiceKind choiceKind;

        /** The choices for this option, if any, and whether or not the choices
         *  are hidden
         *  对应option的选项
         */
        Map<String,Boolean> choices;

        /**
         * 
         * argsNameKey与descrKey的Documentation都放在下面的文件中:
         * com\sun\tools\javac\resources\javac.properties(分国际化版本，中文的是javac_zh_CN.properties)
         * 
         * 比如:-classpath <路径>       指定查找用户类文件和注释处理程序的位置
         * OptionName name             对应CLASSPATH     (-classpath);
         * String argsNameKey          对应opt.arg.path  (<路径>);
         * String descrKey             对应opt.classpath (指定查找用户类文件和注释处理程序的位置); 
         * 
         * 在RecognizedOptions类的getAll()方法里按照各类
         * 参数生成了所有Option(包括它的子类:XOption与HiddenOption)
         */
        Option(OptionName name, String argsNameKey, String descrKey) {
            this.name = name;
            this.argsNameKey = argsNameKey;
            this.descrKey = descrKey;
            char lastChar = name.optionName.charAt(name.optionName.length() - 1);
            hasSuffix = lastChar == ':' || lastChar == '=';
        }

        Option(OptionName name, String descrKey) {
            this(name, null, descrKey);
        }

        Option(OptionName name, String descrKey, ChoiceKind choiceKind, String... choices) {
            this(name, descrKey, choiceKind, createChoices(choices));
        }

        /**
         * 例如： -g:{lines, vars, source} 
         *       createChoice函数会创建这样一个map[{lines:false}, {vars:false}, {source:false}]
         *       
         * @param choices option的选项
         * @return
         */
        private static Map<String,Boolean> createChoices(String... choices) {
            // 这里要保证元素顺序，使用了LinkedHashMap
            Map<String,Boolean> map = new LinkedHashMap<String,Boolean>();
            for (String c: choices)
                map.put(c, false);
            return map;
        }

        Option(OptionName name, String descrKey, ChoiceKind choiceKind,
                Map<String,Boolean> choices) {
            this(name, null, descrKey);
            if (choiceKind == null || choices == null)
                throw new NullPointerException();
            this.choiceKind = choiceKind;
            this.choices = choices;
        }

        @Override
        public String toString() {
            return name.optionName;
        }

        public boolean hasArg() {
            /* 注意这里有另外一个条件：没有后缀的选项
             * 如下面两个选项返回true:
             * -sourcepath <路径>           指定查找输入源文件的位置
             * -bootclasspath <路径>        覆盖引导类文件的位置
             * 
             * 但下面两个选项返回false:
             * -Xbootclasspath:<路径>       覆盖引导类文件的位置
             * -Djava.ext.dirs=<目录>       覆盖安装的扩展目录的位置
             */
            return argsNameKey != null && !hasSuffix;
        }

        // 参数字符串匹配选项模式？
        public boolean matches(String option) {
            if (!hasSuffix)
                return option.equals(name.optionName);

            if (!option.startsWith(name.optionName))
                return false;

            if (choices != null) {
                String arg = option.substring(name.optionName.length());
                if (choiceKind == ChoiceKind.ONEOF)
                    return choices.keySet().contains(arg);
                else {
                    for (String a: arg.split(",+")) {
                        if (!choices.keySet().contains(a))
                            return false;
                    }
                }
            }

            return true;
        }

        /** Print a line of documentation describing this option, if standard.
         * @param out the stream to which to write the documentation
         */
        void help(PrintWriter out) {
            String s = "  " + helpSynopsis();
            out.print(s);
            for (int j = Math.min(s.length(), 28); j < 29; j++) out.print(" ");
            Log.printLines(out, Main.getLocalizedString(descrKey));
        }

        String helpSynopsis() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (argsNameKey == null) {
                if (choices != null) {
                    String sep = "{";
                    for (Map.Entry<String,Boolean> e: choices.entrySet()) {
                        if (!e.getValue()) {
                            sb.append(sep);
                            sb.append(e.getKey());
                            sep = ",";
                        }
                    }
                    sb.append("}");
                }
            } else {
                if (!hasSuffix)
                    sb.append(" ");
                sb.append(Main.getLocalizedString(argsNameKey));
            }

            return sb.toString();
        }

        /** Print a line of documentation describing this option, if non-standard.
         *  @param out the stream to which to write the documentation
         */
        void xhelp(PrintWriter out) {}

        /** Process the option (with arg). Return true if error detected.
         * 标准选项的基类process处理参数方法，如发现错误返回true
         */
        public boolean process(Options options, String option, String arg) {
            if (options != null) {
                if (choices != null) {
                    if (choiceKind == ChoiceKind.ONEOF) {
                        // some clients like to see just one of option+choice set
                        for (String s: choices.keySet())
                            options.remove(option + s);
                        String opt = option + arg;
                        options.put(opt, opt);
                        // some clients like to see option (without trailing ":")
                        // set to arg
                        String nm = option.substring(0, option.length() - 1);
                        options.put(nm, arg);
                    } else {
                        // set option+word for each word in arg
                        for (String a: arg.split(",+")) {
                            String opt = option + a;
                            options.put(opt, opt);
                        }
                    }
                }
                options.put(option, arg);
            }
            return false;
        }

        /** Process the option (without arg). Return true if error detected.
         */
        public boolean process(Options options, String option) {
            if (hasSuffix)
                return process(options, name.optionName, option.substring(name.optionName.length()));
            else
                return process(options, option, option);
        }

        public OptionKind getKind() { return OptionKind.NORMAL; }

        public OptionName getName() { return name; }
    };

    /** A nonstandard or extended (-X) option
     * 非标准选项(也叫扩展选项，用"-X"来查看所有非标准选项)
     */
    static class XOption extends Option {
        XOption(OptionName name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
        }
        XOption(OptionName name, String descrKey) {
            this(name, null, descrKey);
        }
        XOption(OptionName name, String descrKey, ChoiceKind kind, String... choices) {
            super(name, descrKey, kind, choices);
        }
        XOption(OptionName name, String descrKey, ChoiceKind kind, Map<String,Boolean> choices) {
            super(name, descrKey, kind, choices);
        }
        @Override
        void help(PrintWriter out) {}
        @Override
        void xhelp(PrintWriter out) { super.help(out); }
        @Override
        public OptionKind getKind() { return OptionKind.EXTENDED; }
    };

    /** A hidden (implementor) option
     * 隐式选项(内部使用，不会显示)
     */
    static class HiddenOption extends Option {
        HiddenOption(OptionName name) {
            super(name, null, null);
        }
        HiddenOption(OptionName name, String argsNameKey) {
            super(name, argsNameKey, null);
        }
        @Override
        void help(PrintWriter out) {}
        @Override
        void xhelp(PrintWriter out) {}
        @Override
        public OptionKind getKind() { return OptionKind.HIDDEN; }
    };

}
