/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Lint;


/**
 * TODO: describe com.sun.tools.javac.main.OptionName
 * JAVAC 命令详解
 * 一 、结构：
 *      javac [ options ] [ sourcefiles ] [ @files ]
 *      参数可按任意次序排列。
 *      options         命令行选项。
 *      sourcefiles     一个或多个要编译的源文件（例如 MyClass.java）。
 *      @files          一个或多个对源文件进行列表的文件。
 *      
 * 二、说明：
 *      有两种方法可将源代码文件名传递给 javac：
 *      1.如果源文件数量少，在命令行上列出文件名即可。
 *      2.如果源文件数量多，则将源文件名列在一个文件中，名称间用空格或回车行来进行分隔。然后在 javac 命令行中使用该列表文件名，文件名前冠以 @ 字符。
 *        源代码文件名称必须含有 .java 后缀，类文件名称必须含有 .class 后缀，源文件和类文件都必须有识别该类的根名。例如，名为 MyClass 的类将写在名
 *        为 MyClass.java的源文件中，并被编译为字节码类文件 MyClass.class。内部类定义产生附加的类文件。这些类文件的名称将内部类和外部类的名称结合在
 *        一起，例如 MyClass$MyInnerClass.class。应当将源文件安排在反映其包树结构的目录树中。例如，如果将所有的源文件放在 /workspace 中，
 *        那么 com.mysoft.mypack.MyClass 的代码应该在 \workspace\com\mysoft\mypack\MyClass.java 中。缺省情况下，编译器将每个类文件与其源文件放在同一目录中。
 *        可用 -d 选项（请参阅后面的选项）指定其它目标目录。工具读取用 Java 编程语言编写的类和接口定义，并将它们编译成字节码类文件。
 *        
 * 三、查找类型：
 *      当编译源文件时，编译器常常需要它还没有识别出的类型的有关信息。对于源文件中使用、扩展或实现的每个类或接口，编译器都需要其类型信息。
 *      这包括在源文件中没有明确提及、但通过继承提供信息的类和接口。
 *      例如，当扩展 java.applet.Applet 时还要用到 Applet 的祖先类：java.awt.Panel 、 java.awt.Container、 java.awt.Component 和 java.awt.Object。
 *      当编译器需要类型信息时，它将查找定义类型的源文件或类文件。编译器先在自举类及扩展类中查找，然后在用户类路径中查找。用户类路径通过两种途径来定义：
 *      通过设置 CLASSPATH 环境变量或使用 -classpath 命令行选项。（有关详细资料，请参阅设置类路径）。如果使用 -sourcepath 选项，则编译器在 sourcepath 
 *      指定的路径中查找源文件；否则，编译器将在用户类路径中查找类文件和源文件。可用-bootclasspath 和 -extdirs 选项来指定不同的自举类或扩展类；
 *      参阅下面的联编选项。
 *      成功的类型搜索可能生成类文件、源文件或两者兼有。以下是 javac 对各种情形所进行的处理：
 *          搜索结果只生成类文件而没有源文件： javac 使用类文件。
 *          搜索结果只生成源文件而没有类文件： javac 编译源文件并使用由此生成的类文件。
 *          搜索结果既生成源文件又生成类文件： 确定类文件是否过时。若类文件已过时，则 javac 重新编译源文件并使用更新后的类文件。否则， javac 直接使用类文件。
 *              缺省情况下，只要类文件比源文件旧， javac 就认为它已过时。（ -Xdepend 选项指定相对来说较慢但却比较可靠的过程。）
 *          注意： javac 可以隐式编译一些没有在命令行中提及的源文件。用 -verbose 选项可跟踪自动编译。
 *          
 * 四、文件列表：
 *      为缩短或简化 javac 命令，可以指定一个或多个每行含有一个文件名的文件。在命令行中，采用 '@' 字符加上文件名的方法将它指定为文件列表。
 *      当 javac 遇到以 `@' 字符开头的参数时，它对那个文件中所含文件名的操作跟对命令行中文件名的操作是一样的。这使得 Windows 命令行长度不再受限制。
 *      例如，可以在名为 sourcefiles 的文件中列出所有源文件的名称。该文件可能形如：
 *           MyClass1.java
 *           MyClass2.java
 *           MyClass3.java
 *      然后可用下列命令运行编译器：
 *           C:> javac @sourcefiles
 *           
 * 五、选项
 *      编译器有一批标准选项，目前的开发环境支持这些标准选项，将来的版本也将支持它。还有一批附加的非标准选项是目前的虚拟机实现所特有的，将来可能要有变化。
 *      非标准选项以 -X 打头。
 *      
 * 六、标准选项
 *      -classpath 类路径
 *          设置用户类路径，它将覆盖 CLASSPATH 环境变量中的用户类路径。若既未指定 CLASSPATH 又未指定 -classpath，则用户类路径由当前目录构成。有关详细信息，
 *          请参阅设置类路径。若未指定 -sourcepath 选项，则将在用户类路径中查找类文件和源文件。
 *      -d 目录
 *          设置类文件的目标目录。如果某个类是一个包的组成部分，则 javac 将把该类文件放入反映包名的子目录中，必要时创建目录。例如，如果指定 -d c:\myclasses 
 *          并且该类名叫 com.mypackage.MyClass，那么类文件就叫作 c:\myclasses\com\mypackage\MyClass.class。若未指定 -d 选项，则 javac 将把类文件放到与
 *          源文件相同的目录中。注意： -d 选项指定的目录不会被自动添加到用户类路径中。
 *      -deprecation
 *          显示每种不鼓励使用的成员或类的使用或覆盖的说明。没有给出 -deprecation 选项的话， javac 将显示这类源文件的名称：这些源文件使用或覆盖不鼓励使用的成员或类。
 *      -encoding
 *          设置源文件编码名称，例如 EUCJIS/SJIS。若未指定 -encoding 选项，则使用平台缺省的转换器。
 *      -g
 *          生成所有的调试信息，包括局部变量。缺省情况下，只生成行号和源文件信息。
 *      -g:none
 *          不生成任何调试信息。
 *      -g:{关键字列表}
 *          只生成某些类型的调试信息，这些类型由逗号分隔的关键字列表所指定。有效的关键字有：
 *                  source      源文件调试信息
 *                  lines       行号调试信息
 *                  vars        局部变量调试信息
 *      -sourcepath 源路径
 *          指定用以查找类或接口定义的源代码路径。与用户类路径一样，源路径项用分号 (;) 进行分隔，它们可以是目录、JAR 归档文件或 ZIP 归档文件。
 *          如果使用包，那么目录或归档文件中的本地路径名必须反映包名。注意：通过类路径查找的类，如果找到了其源文件，则可能会自动被重新编译。
 *      -verbose
 *          冗长输出。它包括了每个所加载的类和每个所编译的源文件的有关信息。
 *          
 * 七、联编选项
 *      缺省情况下，类是根据与 javac 一起发行的 JDK 自举类和扩展类来编译。但 javac 也支持联编，在联编中，类是根据其它 Java平台实现的自举类和扩展类来进行编译的。
 *      联编时， -bootclasspath 和 -extdirs 的使用很重要；请参阅下面的联编程序示例。
 *          -target 版本
 *              生成将在指定版本的虚拟机上运行的类文件。缺省情况下生成与 1.1 和 1.2 版本的虚拟机都兼容的类文件。
 *              JDK 1.2 中的 javac 所支持的版本有：
 *                  1.1 保证所产生的类文件与 1.1 和 1.2 版的虚拟机兼容。这是缺省状态。
 *                  1.2 生成的类文件可在 1.2 版的虚拟机上运行，但不能在 1.1 版的虚拟机上运行。
 *          -bootclasspath 自举类路径
 *              根据指定的自举类集进行联编。和用户类路径一样，自举类路径项用分号 (;) 进行分隔，它们可以是目录、JAR 归档文件或 ZIP 归档文件。
 *          -extdirs 目录
 *              根据指定的扩展目录进行联编。目录是以分号分隔的目录列表。在指定目录的每个 JAR 归档文件中查找类文件。
 * 
 * 八、非标准选项
 *      -X
 *          显示非标准选项的有关信息并退出。
 *      -Xstdout
 *          将编译器信息送到System.out 中。缺省情况下，编译器信息送到 System.err 中。
 *          
 * 九、联编程序示例
 *      这里我们用 JDK 1.2 的 javac 来编译将在 1.1 版的虚拟机上运行的代码。
 *      C:> javac -target 1.1 -bootclasspath jdk1.1.7\lib\classes.zip \ -extdirs "" OldCode.java
 *      
 *      -target 1.1
 *          JDK 1.2 javac 在缺省状态下也将根据 1.2 版的自举类来进行编译，因此我们需要告诉 javac 让它根据 JDK 1.1 自举类来进行编译。
 *          可用 -bootclasspath 和 -extdirs 选项来达到此目的。不这样做的话，可能会使编译器根据 1.2 版的 API 来进行编译。由于 1.1 版的虚拟机上可能
 *          没有该 1.2 版的 API，因此运行时将出错。
                                选项可确保生成的类文件与 1.1 版的虚拟机兼容。在 JDK1.2 中， 缺省情况下 javac 编译生成的文件是与 1.1 版的虚拟机兼容的，因此并非严格地需要该选项。
                                然而，由于别的编译器可能采用其它的缺省设置，所以提供这一选项将不失为是个好习惯。
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public enum OptionName {
    G("-g"),                                            // 生成所有调试信息
    G_NONE("-g:none"),                                  // 不生成任何调试信息
    G_CUSTOM("-g:"),                                    // -g:{lines, vars, source}只生成某些调试信息
    XLINT("-Xlint"),                                    // 启用建议的警告
    XLINT_CUSTOM("-Xlint:"),                            // 参照enum Lint.LintCategory
                                                        // -Xlint:{all,cast,classfile,deprecation,dep-ann,divzero,empty,fallthrough,
                                                        //         finaly,options,overrides,path,processing,rawtypes,serial,static,
                                                        //         try,unchecked,vararg,-cast,-classfile,-deprecation,-dep-ann,-divzero,
                                                        //         -empty,-fallthrough,-finally,-ptions,-overrides,-path,-processing,
                                                        //         -rawtypes,-serial,-static,-try,-unchecked,-arargs,none} 启用或禁用特定的警告
    
    DIAGS("-XDdiags="),                                 // 
    NOWARN("-nowarn"),                                  // 不生成任何警告
    VERBOSE("-verbose"),                                // 输出有关编译器正在执行的操作消息
    DEPRECATION("-deprecation"),                        // 输出使用已过时的API的源位置
    CLASSPATH("-classpath"),                            // -classpath <路径> 指定查找用户类文件和注解处理程序的位置
    CP("-cp"),                                          // -cp <路径> 作用同-classpath
    SOURCEPATH("-sourcepath"),                          // 指定查找输入源文件的位置
    BOOTCLASSPATH("-bootclasspath"),                    // 覆盖引导类文件的位置
    XBOOTCLASSPATH_PREPEND("-Xbootclasspath/p:"),       // -Xbootclasspath/p:<路径> 置与引导类路径之前
    XBOOTCLASSPATH_APPEND("-Xbootclasspath/a:"),        // -Xbootclasspath/a:<路径> 置与引导类路径之后
    XBOOTCLASSPATH("-Xbootclasspath:"),                 // -Xbootclasspath:<路径> 覆盖引导类文件的位置
    EXTDIRS("-extdirs"),                                // 覆盖所安装扩展的位置
    DJAVA_EXT_DIRS("-Djava.ext.dirs="),                 // -Djava.ext.dirs=<目录> 覆盖所安装扩展的位置
    ENDORSEDDIRS("-endorseddirs"),                      // 覆盖签名和标准路径的位置
    DJAVA_ENDORSED_DIRS("-Djava.endorsed.dirs="),       // -Djava.endorsed.dirs=<目录> 覆盖签名和标准路径的位置
    PROC("-proc:"),                                     // -proc:{none, only}指定是否执行注解处理   (和/或)   编译
    PROCESSOR("-processor"),                            // -processor <class1>[,<class1>,<class2>...] 要执行的注解处理器的名称；绕过默认的搜索进程
    PROCESSORPATH("-processorpath"),                    // -processorpath<路径> 指定查找注解处理器的位置
    D("-d"),                                            // -d <目录> 指定生成的类文件的位置
    S("-s"),                                            // -s <目录> 指定生成的源文件的位置
    IMPLICIT("-implicit:"),                             // -implicit:{none, class} 指定是否为隐式引用文件生成类文件
    ENCODING("-encoding"),                              // -encoding <编码> 指定源文件使用的字符编码
    SOURCE("-source"),                                  // -source <发行版> 提供与指定发行版的源兼容性
    TARGET("-target"),                                  // -target <发行版> 生成特定VM版本的类文件
    VERSION("-version"),                                // 版本信息
    FULLVERSION("-fullversion"),                        //
    HELP("-help"),                                      // 输出标准选项的提要
    A("-A"),                                            // -A关键字[=值] 传递给注解处理器的选项
    X("-X"),                                            // 输出非标准选项的提要
    J("-J"),                                            // -J<标记> 直接将<标记>传递给运行时系统
    MOREINFO("-moreinfo"),                              //
    WERROR("-Werror"),                                  // 出现警告时终止编译
    COMPLEXINFERENCE("-complexinference"),              //
    PROMPT("-prompt"),                                  //
    DOE("-doe"),                                        //
    PRINTSOURCE("-printsource"),                        //
    WARNUNCHECKED("-warnunchecked"),                    //
    XMAXERRS("-Xmaxerrs"),                              // -Xmaxerrs <编号> 设置要输出错误的最大数目
    XMAXWARNS("-Xmaxwarns"),                            // -Xmaxwarns <编号> 设置要输出警告的最大数目
    XSTDOUT("-Xstdout"),                                // -Xstdout <文件名> 重定向标准输出
    XPKGINFO("-Xpkginfo:"),                             // -Xpkginfo:{always, legacy, nonempty} 指定 packeg-info 文件的处理
    XPRINT("-Xprint"),                                  // 输出指定类型的文本表示(调用com.sun.tools.javac.processing.PrintingProcessor注解处理器)
    XPRINTROUNDS("-XprintRounds"),                      // 输出有关注解处理循环的信息
    XPRINTPROCESSORINFO("-XprintProcessorInfo"),        // 输出有关请求处理程序处理那些注解的信息
    XPREFER("-Xprefer:"),                               // -Xprefer:{source, newer} 指定读取文件，当同时找到隐式编译类的源文件和类文件时
    O("-O"),                                            //
    XJCOV("-Xjcov"),                                    //
    XD("-XD"),                                          //
    AT("@"),                                            // @<文件名> 从文件读取选项和文件名
    SOURCEFILE("sourcefile");                           // 源文件

    public final String optionName;

    OptionName(String optionName) {
        this.optionName = optionName;
    }

    @Override
    public String toString() {
        return optionName;
    }

}
