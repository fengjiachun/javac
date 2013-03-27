/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.SoftReference;

/**
 * Implementation of Name.Table that stores all names in a single shared
 * byte array, expanding it as needed. This avoids the overhead incurred
 * by using an array of bytes for each name.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SharedNameTable extends Name.Table {
    // maintain a freelist of recently used name tables for reuse.
    /**
     * freelist用来缓存SharedNameTable对象， 维持着一个最近使用过的SharedNameTable的链表(方便重复使用)
     * freelist中存放着软引用对象，内存空间不够时，会立即被GC回收，不会导致OutOfMemory Error
     */
    private static List<SoftReference<SharedNameTable>> freelist = List.nil();

    /**
     * 目前这个javcac版本好像是没用到create方法，同时freelist的软引用缓存机制也没用到
     * JavaCompiler.close()会调用SharedNameTable.dispose()将SharedNameTable变为软引用加入freelist缓存
     * 但是暂时没发现有调用create方法的地方
     * @param names
     * @return
     */
    static public synchronized SharedNameTable create(Names names) {
        /*
         * 如果缓存freelist不为空，就从freelist里面取得已经实例化的SharedNameTable对象
         * 如果取不到再调用SharedNameTable的构造函数去构造新的实例
         */
        while (freelist.nonEmpty()) {
            SharedNameTable t = freelist.head.get();
            freelist = freelist.tail;   // 去掉头元素head
            if (t != null) {
                return t;
            }
        }
        return new SharedNameTable(names);
    }

    /**
     * 注意这个方法有synchronized锁
     * prepend(t)向链表头部追加对象 t
     * dispose()函数把已经使用完毕的对象 t再重新放回freelist缓存中
     * @param t
     */
    static private synchronized void dispose(SharedNameTable t) {
        freelist = freelist.prepend(new SoftReference<SharedNameTable>(t));
    }

    /** The hash table for names.
     * NameImpl是SharedNameTable的内部类,hashes默认大小 8 * 16 * 16 * 16
     */
    private NameImpl[] hashes;  // hashes其实是一个类似HashMap的数据结构

    /** The shared byte array holding all encountered names.
     * 共享的字节数组，维持着所有遇到的names
     */
    public byte[] bytes;

    /** The mask to be used for hashing
     */
    private int hashMask;

    /** The number of filled bytes in `names'.
     */
    private int nc = 0;

    /** Allocator
     *  @param names The main name table
     *  @param hashSize the (constant) size to be used for the hash table
     *                  needs to be a power of two.
     *  @param nameSize the initial size of the name table.
     */
    public SharedNameTable(Names names, int hashSize, int nameSize) {
        super(names);
        hashMask = hashSize - 1;
        hashes = new NameImpl[hashSize];
        bytes = new byte[nameSize];

    }

    /**
     * NameImpl数组的默认大小为 0x8000(十进制：32768 = 8 * 16 * 16 * 16)
     * bytes数组的默认大小为 0x20000(十进制：131072 = 2 * 16 * 16 * 16 * 16)
     * @param names
     */
    public SharedNameTable(Names names) {
        this(names, 0x8000, 0x20000);
    }

    @Override
    public Name fromChars(char[] cs, int start, int len) {
        int nc = this.nc;
        byte[] bytes = this.bytes;  // bites.length = 2*16*16*16*16
        /*
         * 为啥要乘以3呢，因为将一个char转换为utf时，最大的情况(如一个中文字符)要占3个字节
         * 这段代码的作用是如果this.bytes大小不够，那么再分配现容量2倍大小的空间，如果还不够
         * 空间再乘以2，直到够了为止
         */
        while (nc + len * 3 >= bytes.length) {
            //          System.err.println("doubling name buffer of length " + names.length + " to fit " + len + " chars");//DEBUG
            byte[] newnames = new byte[bytes.length * 2];
            System.arraycopy(bytes, 0, newnames, 0, bytes.length);
            bytes = this.bytes = newnames;
        }
        // nbytes是指将字符数组cs转换成utf格式存到names时所占的字节总数
        int nbytes = Convert.chars2utf(cs, start, bytes, nc, len) - nc;
        // h是cs转换成utf后的哈希码
        int h = hashValue(bytes, nc, nbytes) & hashMask;    // 默认情况下 hashMask = 8 * 16 * 16 * 16 - 1
        // hashes[h]总是存放最近加入的具有相同hashValue的Name
        NameImpl n = hashes[h];     // hashes其实是一个类似HashMap的数据结构
        // 遍历具有相同hashValue的Name的链表
        while (n != null &&
                (n.getByteLength() != nbytes ||
                !equals(bytes, n.index, bytes, nc, nbytes))) {
            n = n.next;
        }
        if (n == null) {
            n = new NameImpl(this);
            n.index = nc;
            n.length = nbytes;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + nbytes;
            if (nbytes == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public Name fromUtf(byte[] cs, int start, int len) {
        int h = hashValue(cs, start, len) & hashMask;
        NameImpl n = hashes[h];
        byte[] names = this.bytes;
        while (n != null &&
                (n.getByteLength() != len || !equals(names, n.index, cs, start, len))) {
            n = n.next;
        }
        if (n == null) {
            int nc = this.nc;
            while (nc + len > names.length) {
                //              System.err.println("doubling name buffer of length + " + names.length + " to fit " + len + " bytes");//DEBUG
                byte[] newnames = new byte[names.length * 2];
                System.arraycopy(names, 0, newnames, 0, names.length);
                names = this.bytes = newnames;
            }
            System.arraycopy(cs, start, names, nc, len);
            n = new NameImpl(this);
            n.index = nc;
            n.length = len;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + len;
            if (len == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public void dispose() {
        dispose(this);
    }

    /**
     * 本身是个链表结构
     */
    static class NameImpl extends Name {
        /** The next name occupying the same hash bucket.
         */
        NameImpl next;

        /** The index where the bytes of this name are stored in the global name
         *  buffer `byte'.
         */
        int index;

        /** The number of bytes in this name.
         */
        int length;

        NameImpl(SharedNameTable table) {
            super(table);
        }

        @Override
        public int getIndex() { // 作为Keywords.key Token数组的索引
            return index;
        }

        @Override
        public int getByteLength() {
            return length;
        }

        @Override
        public byte getByteAt(int i) {
            return getByteArray()[index + i];
        }

        @Override
        public byte[] getByteArray() {
            return ((SharedNameTable) table).bytes;
        }

        @Override
        public int getByteOffset() {
            return index;
        }

        /** Return the hash value of this name.
         */
        public int hashCode() {
            return index;
        }

        /** Is this name equal to other?
         */
        public boolean equals(Object other) {
            if (other instanceof Name)
                return
                    table == ((Name)other).table && index == ((Name) other).getIndex();
            else return false;
        }

    }

}
