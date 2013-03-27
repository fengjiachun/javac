/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac;

import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;


/**
 * <b>Unsupported</b> entry point for starting javac from an IDE.
 *
 * 这个类在JDK1.6内不是有效的，默认情况下它不会被编译进tools.jar。(但是JDK1.7版本中tools.jar中已经有这个类了)
 * 它的存在是为了方便使用IDE编辑编译器的源码。
 * 提供一个图形界面，可以选择要编译的文件。
 * <p><b>Note:</b> this class is not available in the JDK.  It is not
 * compiled by default and will not be in tools.jar.  It is designed
 * to be useful when editing the compiler sources in an IDE (as part
 * of a <em>project</em>).  Simply ensure that this class is added to
 * the project and make it the main class of the project.</p>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
class Launcher {
    public static void main(String... args) {
        // ToolProvider:
        // 为查找工具提供者提供方法，例如，编译器的提供者。此类实现 ServiceLoader 功能
        // getSystemJavaCompiler() : 获取此平台提供的 Java编程语言编译器
        // defaultJavaCompiler : com.sun.tools.javac.api.JavacTool
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        // 提供一个图形界面，可以选择要编译的文件
        JFileChooser fileChooser;
        // 各个平台的实现不同，在Windows平台中，Preferences的实现是将数据保存在注册表中的
        Preferences prefs = Preferences.userNodeForPackage(Launcher.class);
        if (args.length > 0)
            fileChooser = new JFileChooser(args[0]);
        else {
            // prefs可以在注册表中缓存上次打开的文件路径，
            // 具体参考java.util.prefs.AbstractPreferences的get和put方法
            String fileName = prefs.get("recent.file", null);
            fileChooser = new JFileChooser();
            if (fileName != null) {
                fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new File(fileName));
            }
        }
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            String fileName = fileChooser.getSelectedFile().getPath();
            // 将fileName放入了注册表中，
            // 我的测试代码在我的机器上产生的注册表信息是这样的：
            //    HKEY_USERS\S-1-5-21-2927120809-1180443650-1650080734-1000\Software\JavaSoft\Prefs\com\sun\tools\javac
            //    名称: recent.file
            //    数据: /D:///Workspace///J/S/E//workspace///Compiler_javac//test-files//java-files///For/Launcher_trace.java
            prefs.put("recent.file", fileName);
            // edit by me 这里"/tmp"是class文件的输出路径，为了测试我修改一下
//            javac.run(System.in, null, null, "-d", "/tmp", fileName);
            javac.run(
                    System.in, null, null,
                    "-d",
                    "D:/Workspace/JSE/workspace/Compiler_javac/test-files/class-files",
                    fileName);
            // end
        }
    }
}
