/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.main;

import static com.sun.tools.javac.main.OptionName.D;
import static com.sun.tools.javac.main.OptionName.FULLVERSION;
import static com.sun.tools.javac.main.OptionName.HELP;
import static com.sun.tools.javac.main.OptionName.S;
import static com.sun.tools.javac.main.OptionName.SOURCE;
import static com.sun.tools.javac.main.OptionName.TARGET;
import static com.sun.tools.javac.main.OptionName.VERSION;
import static com.sun.tools.javac.main.OptionName.X;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.MissingResourceException;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.JavacOption.Option;
import com.sun.tools.javac.main.RecognizedOptions.OptionHelper;
import com.sun.tools.javac.processing.AnnotationProcessingError;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.FatalError;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.PropagatedException;

/**
 * This class provides a commandline interface to the GJC compiler.
 * 这个类为GJC(通用的编译器)提供一个命令行接口
 * GJC: Generic Java Compiler(http://homepages.inf.ed.ac.uk/wadler/gj/)
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at your own risk. This code and its internal interfaces are
 * subject to change or deletion without notice.</b>
 */
public class Main {

	/**
	 * The name of the compiler, for use in diagnostics.
	 * 编译器名字， 打印帮助信息、版本信息等会用到这个变量
	 * 可能是javac
	 * 也可能是javacTask:来自{link JavacTool}#getTask()
	 */
	String ownName;

	/**
	 * The writer to use for diagnostic output.
	 * 诊断输出流
	 */
	PrintWriter out;

	/**
	 * If true, certain errors will cause an exception, such as command line arg
	 * errors, or exceptions in user provided code.
	 * 如果为true，某些错误会导致异常，比如命令行参数错误，或者用户提供的代码中的异常
	 * 从命令行启动com.sun.tools.javac.Main时apiMode是false
	 * 在java代码中动态编译.java文件时会调用下面方法：
	 * {link JavacTaskImpl}#call()方法中会把apiMode设置为true
	 * 例如下面这段动态编译的代码：
	 * // compiler实际类型:com.sun.tools.javac.api.JavacTool
	 * javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
	 * // standardJavaFileManager实际类型 :com.sun.tools.javac.file.JavacFileManager
	 * javax.tools.StandardJavaFileManager standardJavaFileManager = compiler.getStandardFileManager(null, null, null);
	 * Iterable<? extends javax.tools.JavaFileObject> iterable = standardJavaFileManager.getJavaFileObjects(javaFileName);
	 * // compilationTask实际类型:com.sun.tools.javac.api.JavacTaskImpl
	 * javax.tools.JavaCompiler.CompilationTask compilationTask = compiler.getTask(null, standardJavaFileManager, null, null, null, iterable);
	 * // 调用com.sun.tools.javac.api.JavacTaskImpl.call(); 函数中会把apiMode设置为true
	 * compilationTask.call();
	 * standardJavaFileManager.close();
	 */
	boolean apiMode;

	/**
	 * Result codes.
	 * an integer equivalent to the exit value from invoking javac
	 */
	static final int EXIT_OK = 0, // Compilation completed with no errors. 编译完成，没有错误
			EXIT_ERROR = 1, // Completed but reported errors. 完成，但是有报告错误
			EXIT_CMDERR = 2, // Bad command-line arguments. 非法的命令行参数
			EXIT_SYSERR = 3, // System error or resource exhaustion. 系统错误或者资源枯竭
			EXIT_ABNORMAL = 4; // Compiler terminated abnormally. 编译器异常终止

	// javac选项 option.process()调用
	private Option[] recognizedOptions = RecognizedOptions.getJavaCompilerOptions(new OptionHelper() {

		// 设置标准输出
		public void setOut(PrintWriter out) {
			Main.this.out = out;
		}

		public void error(String key, Object... args) {
			Main.this.error(key, args);
		}

		// 打印版本信息
		public void printVersion() {
			// com/sun/tools/javac/resources/version.properties文件不存在,所以无法取得version信息
			Log.printLines(Main.this.out, getLocalizedString("version", Main.this.ownName, JavaCompiler.version()));
		}

		public void printFullVersion() {
			// com/sun/tools/javac/resources/version.properties文件不存在,所以无法取得version信息
			Log.printLines(Main.this.out, getLocalizedString("fullVersion", Main.this.ownName, JavaCompiler.fullVersion()));
		}

		// 帮助信息
		public void printHelp() {
			help();
		}

		// 扩展帮助信息 javac -X
		public void printXhelp() {
			xhelp();
		}

		public void addFile(File f) {
			if (!Main.this.filenames.contains(f)) {
				Main.this.filenames.append(f);
			}
		}

		public void addClassName(String s) {
			Main.this.out.println("addClassName: " + s);
			Main.this.classnames.append(s);
		}

	});

	/**
	 * Construct a compiler instance.
	 * 构造一个编译器实例
	 */
	public Main(String name) {
		this(name, new PrintWriter(System.err, true));
	}

	/**
	 * Construct a compiler instance.
	 * 构造一个编译器实例，指定输出流
	 */
	public Main(String name, PrintWriter out) {
		this.ownName = name; // ownName = javac 设置编译器名称
		this.out = out; // 设置输出流(标准错误输出)
	}

	/**
	 * A table of all options that's passed to the JavaCompiler constructor.
	 * 命令行的参数映射到这里
	 */
	private Options options = null;

	/**
	 * The list of source files to process
	 * 从命令行传递过来的待编译的.java源文件列表
	 */
	public ListBuffer<File> filenames = null; // XXX sb protected

	/**
	 * List of class files names passed on the command line
	 * 从命令行传递过来的class文件列表 ，显式请求注解处理(javac -processor XXX)和-Xprint时接受类名称
	 * RecognizedOptions中也有以下代码:helper.addClassName(s);
	 */
	public ListBuffer<String> classnames = null; // XXX sb protected

	/**
	 * Print a string that explains usage.
	 * 打印帮助信息
	 */
	void help() {
		Log.printLines(this.out, getLocalizedString("msg.usage.header", this.ownName));
		for (Option recognizedOption : this.recognizedOptions) {
			recognizedOption.help(this.out);
		}
		this.out.println();
	}

	/**
	 * Print a string that explains usage for X options.
	 * javac -X，非标准选项的帮助信息
	 */
	void xhelp() {
		for (Option recognizedOption : this.recognizedOptions) {
			recognizedOption.xhelp(this.out);
		}
		this.out.println();
		Log.printLines(this.out, getLocalizedString("msg.usage.nonstandard.footer"));
	}

	/**
	 * Report a usage error.
	 */
	void error(String key, Object... args) {
		if (this.apiMode) {
			String msg = getLocalizedString(key, args);
			throw new PropagatedException(new IllegalStateException(msg));
		}
		warning(key, args);
		Log.printLines(this.out, getLocalizedString("msg.usage", this.ownName));
	}

	/**
	 * Report a warning.
	 */
	void warning(String key, Object... args) {
		Log.printLines(this.out, this.ownName + ": " + getLocalizedString(key, args));
	}

	// 根据string取得option
	public Option getOption(String flag) {
		for (Option option : this.recognizedOptions) {
			if (option.matches(flag)) {
				return option;
			}
		}
		return null;
	}

	public void setOptions(Options options) {
		if (options == null) {
			throw new NullPointerException();
		}
		this.options = options;
	}

	/**
	 * 在java代码中动态编译.java文件时会调用下面方法：
	 * com.sun.tools.javac.api.JavacTaskImpl#call()方法中会把apiMode设置为true
	 */
	public void setAPIMode(boolean apiMode) {
		this.apiMode = apiMode;
	}

	/**
	 * 处理所有的命令行参数并存入options中
	 * 返回源代码文件列表
	 * Process command line arguments: store all command line options in
	 * `options' table and return all source filenames.
	 * @param flags
	 *            The array of command line arguments.
	 */
	public List<File> processArgs(String[] flags) { // XXX sb protected
		try {
			int ac = 0;
			// 循环处理flags将flag匹配到一个Option上,每个命令行选项都转化成一个Option
			while (ac < flags.length) {
				String flag = flags[ac];
				ac++;

				/*
				 * Option数据结构：
				 * OptionName name; // 选项名称
				 * String argsNameKey; // 后缀参数的名称 比如 -d <目录名> （argsNameKey=目录名）
				 * String descrKey; // 帮助信息的key
				 * boolean hasSuffix; // 冒号： 等号= 啥的
				 * ChoiceKind choiceKind;
				 * Map<String,Boolean> choices;
				 */
				Option option = null;

				if (flag.length() > 0) {
					// quick hack to speed up file processing:
					// if the option does not begin with '-', there is no need
					// to check
					// most of the compiler options.
					// 这段代码有点小技巧：
					// 因为javac的命令行选项名称都是以"-"开头的，recognizedOptions数组中除了
					// 最后一个选项不以'-'字符开头外，其它所有选项名称都是以'-'字符开头的。
					// AT("@")例外，因为函数执行到这里"@<文件名>"已经被CommandLine.parse(args)处理了
					// 如果在javac命令行中出现不是以'-'字符开头的选项，则查找位置firstOptionToCheck
					// 从recognizedOptions数组最末尾开始,(也就是直接与recognizedOptions数组的最后一个选项比较)
					// 它要么是要编译的源文件，要么是错误的选项。
					int firstOptionToCheck = flag.charAt(0) == '-' ? 0 : this.recognizedOptions.length - 1;
					for (int j = firstOptionToCheck; j < this.recognizedOptions.length; j++) {
						if (this.recognizedOptions[j].matches(flag)) { // 调用RecognizedOptions#getAll()生成的那些Option的matches方法
							// 每个命令行选项都转化成一个Option
							option = this.recognizedOptions[j];
							break;
						}
					}
				}
				// 错误不存在的javac选项
				if (option == null) {
					error("err.invalid.flag", flag);
					return null;
				}
				// 将javac选项(flag)和选项的参数(operand)放入options.values中，
				// options.values是一个LinkedHashMap<String, String>容器
				// 很多option都重写了process方法,大多数process()内部都是把flag与operand构成一个<K,V>对
				// 如果没有operand就把flag与flag构成一个<K,V>对
				if (option.hasArg()) {
					// 这个选项有后缀操作数，比如javac: -d 需要后缀操作数
					if (ac == flags.length) {
						error("err.req.arg", flag);
						return null;
					}
					String operand = flags[ac];
					ac++; // 因为使用了紧跟着的一个命令行参数，所以这里把ac加1
					if (option.process(this.options, flag, operand)) {
						return null;
					}
				} else {
					// 如果是源文件，将对应File对象放进this.filenames
					if (option.process(this.options, flag)) {
						return null;
					}
				}
			}

			// 主要处理已经完成（将flags包装成Options），往下是做一些check
			if (!checkDirectory(D)) {
				return null;
			}
			if (!checkDirectory(S)) {
				return null;
			}
			// 需要在javac命令行中指定-source选项，默认1.7
			// -source:提供与指定发行版的源兼容性
			String sourceString = this.options.get(SOURCE);
			Source source = (sourceString != null) ? Source.lookup(sourceString) : Source.DEFAULT;
			// 需要在javac命令行中指定-target选项，默认1.7
			// -target:生成特定 VM 版本的类文件
			String targetString = this.options.get(TARGET);
			Target target = (targetString != null) ? Target.lookup(targetString) : Target.DEFAULT;
			// We don't check source/target consistency for CLDC, as J2ME
			// profiles are not aligned with J2SE targets; moreover, a
			// single CLDC target may have many profiles. In addition,
			// this is needed for the continued functioning of the JSR14
			// prototype.
			// 检查source/target兼容性
			if (Character.isDigit(target.name.charAt(0))) {
				if (target.compareTo(source.requiredTarget()) < 0) {
					if (targetString != null) {
						if (sourceString == null) {
							warning("warn.target.default.source.conflict", targetString, source.requiredTarget().name);
						} else {
							warning("warn.source.target.conflict", sourceString, source.requiredTarget().name);
						}
						return null;
					} else {
						target = source.requiredTarget();
						this.options.put("-target", target.name);
					}
				} else {
					if (targetString == null && !source.allowGenerics()) {
						target = Target.JDK1_4;
						this.options.put("-target", target.name);
					}
				}
			}

			// handle this here so it works even if no other options given
			// javac中没有showClass这个选项，不知道怎么使用
			// 按我的理解options中只能存在RecognizedOptions.javacOptions中含有的选项
			// 参照本方法体内这行代码的逻辑：if (recognizedOptions[j].matches(flag)) {
			String showClass = this.options.get("showClass");
			if (showClass != null) {
				if (showClass.equals("showClass")) {
					showClass = "com.sun.tools.javac.Main";
				}
				showClass(showClass); // 显示类的位置并校验
			}

			// 返回源代码文件列表
			return this.filenames.toList();
		} finally {
			this.out.println("ListBuffer<File> filenames.size() = " + this.filenames.size());
			this.out.println("ListBuffer<File> filenames = " + this.filenames.toList().toString());
			this.out.println("ListBuffer<String> classnames.size() = " + this.classnames.size());
			this.out.println("ListBuffer<String> classnames = " + this.classnames.toList().toString());
		}
	}

	// where
	/**
	 * 检查-d选项的后缀操作数是不是一个目录
	 * @param optName -d\-s
	 * @return 如果没指定-d\-s选项直接返回true，不存在返回false，是目录返回true，不是目录返回false
	 */
	private boolean checkDirectory(OptionName optName) {
		String value = this.options.get(optName);
		if (value == null) {
			return true;
		}
		File file = new File(value);
		if (!file.exists()) {
			error("err.dir.not.found", value);
			return false;
		}
		if (!file.isDirectory()) {
			error("err.file.not.directory", value);
			return false;
		}
		return true;
	}

	/**
	 * Programmatic interface for main function.
	 * @param args
	 *            The command line parameters.
	 *            命令行参数
	 */
	public int compile(String[] args) {
		// 类似ThreadLocal，不过这是用户级别的上下文内容，而不是当前线程
		// 详细内容参照com.sun.tools.javac.util.Context类注释
		Context context = new Context();

		// 注册JavacFileManager到Context中
		// 在context.get(JavaFileManager.class)被调用时会执行make(Context c)回调函数（懒加载）来
		// 构造JavaFileManager实例
		// 在 Log设置好之前(设置方法:context.put(Log.outKey, out))， 不能构造JavaFileManager对象实例
		// JavacFileManager类提供源文件、类文件和其他文件的访问接口，由编译器和相关工具使用
		JavacFileManager.preRegister(context); // can't create it until Log has been set up
		int result = compile(args, context);
		// ReadTag
		if (this.fileManager instanceof JavacFileManager) {
			// A fresh context was created above, so jfm must be a
			// JavacFileManager
			((JavacFileManager) this.fileManager).close();
		}
		return result;
	}

	public int compile(String[] args, Context context) {
		return compile(args, context, List.<JavaFileObject> nil(), null);
	}

	/**
	 * Programmatic interface for main function.
	 * @param args 命令行参数
	 * @param context 用户级别上下文内容
	 * @param fileObjects 等着被编译的文件对象列表
	 * @param processors 处理器(注解处理器?)
	 * @return an integer equivalent to the exit value from invoking javac
	 */
	public int compile(String[] args, Context context, List<JavaFileObject> fileObjects, Iterable<? extends Processor> processors) {
		if (this.options == null) {
			this.options = Options.instance(context); // creates a new one
		}

		// 这两个实例字段的值在调用processArgs()方法时，
		// 都是通过RecognizedOptions.HiddenOption(SOURCEFILE)的process()得到的
		this.filenames = new ListBuffer<File>();
		this.classnames = new ListBuffer<String>();
		JavaCompiler comp = null;
		/*
		 * TODO: Logic below about what is an acceptable command line should be
		 * updated to take annotation processing semantics into account.
		 */
		try {
			// 如果命令行参数为空并且“等着被编译的文件对象列表”也为空（这里是一定为空，因为
			// compile(String[] args, Context context)传过来的就是空）
			// 就直接打印帮助信息,并返回“退出状态” （Bad command-line arguments 非法的命令行参数）
			if (args.length == 0 && fileObjects.isEmpty()) {
				// JUnitTestMethod:testMain_nullArgs()
				help();
				return EXIT_CMDERR; // 非法的命令行参数
			}

			List<File> files;
			try {
				// 处理所有的命令行参数并存入options中
				// 返回源代码文件列表
				files = processArgs(CommandLine.parse(args));
				if (files == null) {
					// null signals an error in options, abort
					return EXIT_CMDERR; // 非法的命令行参数
				} else if (files.isEmpty() && fileObjects.isEmpty() && this.classnames.isEmpty()) {
					// it is allowed to compile nothing if just asking for help
					// or version info
					// 可以不编译任何.java文件，比如可以打印帮助信息、版本信息等
					// -help、-X、-version、-fullversion
					if (this.options.isSet(HELP) || this.options.isSet(X) || this.options.isSet(VERSION) || this.options.isSet(FULLVERSION)) {
						return EXIT_OK;
					}
					// -processor、-processorpath、-proc:{only}、-Xprint
					if (JavaCompiler.explicitAnnotationProcessingRequested(this.options)) {
						// 注解处理相关
						error("err.no.source.files.classes");
					} else {
						error("err.no.source.files");
					}
					return EXIT_CMDERR; // 非法的命令行参数
				}
			} catch (java.io.FileNotFoundException e) {
				Log.printLines(this.out, this.ownName + ": " + getLocalizedString("err.file.not.found", e.getMessage()));
				return EXIT_SYSERR; // 系统错误或者资源枯竭
			}
			boolean forceStdOut = this.options.isSet("stdout");
			if (forceStdOut) {
				this.out.flush();
				this.out = new PrintWriter(System.out, true);
			}

			context.put(Log.outKey, this.out);

			// allow System property in following line as a Mustang legacy
			// CacheFSInfo extends FSInfo:Caching implementation of FSInfo
			// FSInfo:Get meta-info about files
			// OptionName不存在nonBatchMode这个选项
			boolean batchMode = (this.options.isUnset("nonBatchMode") && System.getProperty("nonBatchMode") == null);
			if (batchMode) {
				CacheFSInfo.preRegister(context);
			}

			this.fileManager = context.get(JavaFileManager.class);

			// 取得编译器实例
			comp = JavaCompiler.instance(context);
			if (comp == null) {
				return EXIT_SYSERR;
			}

			Log log = Log.instance(context);

			if (!files.isEmpty()) {
				// add filenames to fileObjects
				comp = JavaCompiler.instance(context);
				List<JavaFileObject> otherFiles = List.nil();
				JavacFileManager dfm = (JavacFileManager) this.fileManager;
				for (JavaFileObject fo : dfm.getJavaFileObjectsFromFiles(files)) {
					otherFiles = otherFiles.prepend(fo);
				}
				for (JavaFileObject fo : otherFiles) {
					fileObjects = fileObjects.prepend(fo);
				}
			}

			// 这个是核心处理了，编译fileObjects中所有的源文件
			comp.compile(fileObjects, this.classnames.toList(), processors);

			if (log.expectDiagKeys != null) {
				if (log.expectDiagKeys.isEmpty()) {
					Log.printLines(log.noticeWriter, "all expected diagnostics found");
					return EXIT_OK;
				} else {
					Log.printLines(log.noticeWriter, "expected diagnostic keys not found: " + log.expectDiagKeys);
					return EXIT_ERROR;
				}
			}

			if (comp.errorCount() != 0) {
				return EXIT_ERROR;
			}
		} catch (IOException ex) {
			ioMessage(ex);
			return EXIT_SYSERR;
		} catch (OutOfMemoryError ex) {
			resourceMessage(ex);
			return EXIT_SYSERR;
		} catch (StackOverflowError ex) {
			resourceMessage(ex);
			return EXIT_SYSERR;
		} catch (FatalError ex) {
			feMessage(ex);
			return EXIT_SYSERR;
		} catch (AnnotationProcessingError ex) {
			if (this.apiMode) {
				throw new RuntimeException(ex.getCause());
			}
			apMessage(ex);
			return EXIT_SYSERR;
		} catch (ClientCodeException ex) {
			// as specified by javax.tools.JavaCompiler#getTask
			// and javax.tools.JavaCompiler.CompilationTask#call
			throw new RuntimeException(ex.getCause());
		} catch (PropagatedException ex) {
			throw ex.getCause();
		} catch (Throwable ex) {
			// Nasty. If we've already reported an error, compensate
			// for buggy compiler error recovery by swallowing thrown
			// exceptions.
			if (comp == null || comp.errorCount() == 0 || this.options == null || this.options.isSet("dev")) {
				bugMessage(ex);
			}
			return EXIT_ABNORMAL;
		} finally {
			if (comp != null) {
				try {
					comp.close();
				} catch (ClientCodeException ex) {
					throw new RuntimeException(ex.getCause());
				}
			}
			this.filenames = null;
			this.options = null;
		}
		return EXIT_OK;
	}

	/**
	 * Print a message reporting an internal error.
	 */
	void bugMessage(Throwable ex) {
		Log.printLines(this.out, getLocalizedString("msg.bug", JavaCompiler.version()));
		ex.printStackTrace(this.out);
	}

	/**
	 * Print a message reporting a fatal error.
	 */
	void feMessage(Throwable ex) {
		Log.printLines(this.out, ex.getMessage());
		if (ex.getCause() != null && this.options.isSet("dev")) {
			ex.getCause().printStackTrace(this.out);
		}
	}

	/**
	 * Print a message reporting an input/output error.
	 */
	void ioMessage(Throwable ex) {
		Log.printLines(this.out, getLocalizedString("msg.io"));
		ex.printStackTrace(this.out);
	}

	/**
	 * Print a message reporting an out-of-resources error.
	 */
	void resourceMessage(Throwable ex) {
		Log.printLines(this.out, getLocalizedString("msg.resource"));
		// System.out.println("(name buffer len = " + Name.names.length + " " +
		// Name.nc);//DEBUG
		ex.printStackTrace(this.out);
	}

	/**
	 * Print a message reporting an uncaught exception from an annotation
	 * processor.
	 */
	void apMessage(AnnotationProcessingError ex) {
		Log.printLines(this.out, getLocalizedString("msg.proc.annotation.uncaught.exception"));
		ex.getCause().printStackTrace(this.out);
	}

	/**
	 * Display the location and checksum of a class.
	 * 显示类的位置并校验
	 */
	void showClass(String className) {
		this.out.println("javac: show class: " + className);
		URL url = getClass().getResource('/' + className.replace('.', '/') + ".class");
		if (url == null) {
			this.out.println("  class not found");
		} else {
			this.out.println("  " + url);
			try {
				final String algorithm = "MD5";
				byte[] digest;
				MessageDigest md = MessageDigest.getInstance(algorithm);
				DigestInputStream in = new DigestInputStream(url.openStream(), md);
				try {
					byte[] buf = new byte[8192];
					int n;
					do {
						n = in.read(buf);
					} while (n > 0);
					digest = md.digest();
				} finally {
					in.close();
				}
				StringBuilder sb = new StringBuilder();
				for (byte b : digest) {
					sb.append(String.format("%02x", b));
				}
				this.out.println("  " + algorithm + " checksum: " + sb);
			} catch (Exception e) {
				this.out.println("  cannot compute digest: " + e);
			}
		}
	}

	private JavaFileManager fileManager;

	/* ************************************************************************
	 * Internationalization 国际化相关
	 * ************************************************************************
	 */

	/**
	 * Find a localized string in the resource bundle.
	 * 从XXX.properties资源文件中取得对应信息
	 * @param key
	 *            The key for the localized string.
	 */
	public static String getLocalizedString(String key, Object... args) { // FIXME
																			// sb
																			// private
		try {
			if (messages == null) {
				messages = new JavacMessages(javacBundleName);
			}
			return messages.getLocalizedString("javac." + key, args);
		} catch (MissingResourceException e) {
			throw new Error("Fatal Error: Resource for javac is missing", e);
		}
	}

	public static void useRawMessages(boolean enable) {
		if (enable) {
			messages = new JavacMessages(javacBundleName) {

				@Override
				public String getLocalizedString(String key, Object... args) {
					return key;
				}
			};
		} else {
			messages = new JavacMessages(javacBundleName);
		}
	}

	private static final String javacBundleName = "com.sun.tools.javac.resources.javac";

	private static JavacMessages messages;
}
