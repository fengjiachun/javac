/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.*;

import javax.tools.JavaFileManager;

import com.sun.tools.javac.file.JavacFileManager;

/**
 * Support for an abstract context, modelled loosely after ThreadLocal
 * but using a user-provided context instead of the current thread.
 * javac中几乎所有的对象都由Context维持着单例引用
 *
 * <p>Within the compiler, a single Context is used for each
 * invocation of the compiler.  The context is then used to ensure a
 * single copy of each compiler phase exists per compiler invocation.
 *
 * <p>The context can be used to assist in extending the compiler by
 * extending its components.  To do that, the extended component must
 * be registered before the base component.  We break initialization
 * cycles by (1) registering a factory for the component rather than
 * the component itself, and (2) a convention for a pattern of usage
 * in which each base component registers itself by calling an
 * instance method that is overridden in extended components.  A base
 * phase supporting extension would look something like this:
 *
 * <p><pre>
 * public class Phase {
 *     protected static final Context.Key<Phase> phaseKey =
 *         new Context.Key<Phase>();
 *
 *     public static Phase instance(Context context) {
 *         Phase instance = context.get(phaseKey);
 *         if (instance == null)
 *             // the phase has not been overridden
 *             instance = new Phase(context);
 *         return instance;
 *     }
 *
 *     protected Phase(Context context) {
 *         context.put(phaseKey, this);
 *         // other intitialization follows...
 *     }
 * }
 * </pre>
 *
 * <p>In the compiler, we simply use Phase.instance(context) to get
 * the reference to the phase.  But in extensions of the compiler, we
 * must register extensions of the phases to replace the base phase,
 * and this must be done before any reference to the phase is accessed
 * using Phase.instance().  An extended phase might be declared thus:
 *
 * <p><pre>
 * public class NewPhase extends Phase {
 *     protected NewPhase(Context context) {
 *         super(context);
 *     }
 *     public static void preRegister(final Context context) {
 *         context.put(phaseKey, new Context.Factory<Phase>() {
 *             public Phase make() {
 *                 return new NewPhase(context);
 *             }
 *         });
 *     }
 * }
 * </pre>
 * 
 * <p>And is registered early in the extended compiler like this
 *
 * <p><pre>
 *     NewPhase.preRegister(context);
 * </pre>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Context {
    /** The client creates an instance of this class for each key.
     */
    public static class Key<T> {
        // note: we inherit identity equality from Object.
        // Context上下文中对象的标识
    }

    /**
     * The client can register a factory for lazy creation of the
     * instance.
     * 需要延迟实例化的对象只要实现这个接口的即可
     */
    public static interface Factory<T> {
        /*
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            // 不能立即创建JavacFileManager实例，在回调函数中创建实例
            public JavaFileManager make(Context c) {
                return new JavacFileManager(c, true, null);
            }
        });
         */
        T make(Context c);
    };

    /**
     * The underlying map storing the data.
     * We maintain the invariant that this table contains only
     * mappings of the form
     * Key<T> -> T or Key<T> -> Factory<T> */
    private Map<Key<?>, Object> ht = new HashMap<Key<?>, Object>();

    /** Set the factory for the key in this context. */
    public <T> void put(Key<T> key, Factory<T> fac) {
        checkState(ht);
        Object old = ht.put(key, fac);
        if (old != null)
            throw new AssertionError("duplicate context value");
        checkState(ft);
        ft.put(key, fac); // cannot be duplicate if unique in ht
    }

    /** Set the value for the key in this context. */
    public <T> void put(Key<T> key, T data) {
        if (data instanceof Factory<?>)
            throw new AssertionError("T extends Context.Factory");
        checkState(ht);
        /* 延迟实例化的情况，先放进ht的是一个Factory，
         * 在Context.get()被调用的时候会调用对应的make()去创建对象实例
         * 当对象真正被创建时应该把Context中的Factory替换掉
         */ 
        Object old = ht.put(key, data);
        // 如果ht中旧对象old != null,那old应该是个Factory，否则多半是出现重复了，Context中的对象都应该是单例的
        if (old != null && !(old instanceof Factory<?>) && old != data && data != null)
            throw new AssertionError("duplicate context value");
    }

    /** Get the value for the key in this context. */
    public <T> T get(Key<T> key) {
        checkState(ht);
        Object o = ht.get(key);
        if (o instanceof Factory<?>) {  // 如果从ht中取出的是Factory，则调用make创建相关实例对象
            Factory<?> fac = (Factory<?>)o;
            o = fac.make(this);
            if (o instanceof Factory<?>)
                throw new AssertionError("T extends Context.Factory");
            Assert.check(ht.get(key) == o);
        }

        /* The following cast can't fail unless there was
         * cheating elsewhere, because of the invariant on ht.
         * Since we found a key of type Key<T>, the value must
         * be of type T.
         */
        return Context.<T>uncheckedCast(o);
    }

    public Context() {}

    /**
     * The table of preregistered factories.
     * 保存延迟实例化需要的Factory
     */
    private Map<Key<?>, Factory<?>> ft = new HashMap<Key<?>, Factory<?>>();

    public Context(Context prev) {
        kt.putAll(prev.kt);     // retain all implicit keys
        ft.putAll(prev.ft);     // retain all factory objects
        ht.putAll(prev.ft);     // init main table with factories
    }

    /**
     * The key table, providing a unique Key<T> for each Class<T>.
     * 保存延迟实例化需要的key
     */
    private Map<Class<?>, Key<?>> kt = new HashMap<Class<?>, Key<?>>();

    private <T> Key<T> key(Class<T> clss) {
        checkState(kt);
        Key<T> k = uncheckedCast(kt.get(clss));
        if (k == null) {
            k = new Key<T>();
            kt.put(clss, k);
        }
        return k;
    }

    public <T> T get(Class<T> clazz) {
        return get(key(clazz));
    }

    public <T> void put(Class<T> clazz, T data) {
        put(key(clazz), data);
    }
    public <T> void put(Class<T> clazz, Factory<T> fac) {
        put(key(clazz), fac);
    }

    /**
     * TODO: This method should be removed and Context should be made type safe.
     * This can be accomplished by using class literals as type tokens.
     */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object o) {
        return (T)o;
    }

    public void dump() {
        for (Object value : ht.values())
            System.err.println(value == null ? null : value.getClass());
    }

    public void clear() {
        ht = null;
        kt = null;
        ft = null;
    }

    private static void checkState(Map<?, ?> t) {
        if (t == null)
            throw new IllegalStateException();
    }
}
