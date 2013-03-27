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

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** A class for constructing lists by appending elements. Modelled after
 *  java.lang.StringBuffer.
 *  用append(元素)构造链表，类似java.lang.StringBuffer
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ListBuffer<A> extends AbstractQueue<A> {

    /**
     * 
     * @return 返回一个ListBuffer<T>实例
     */
    public static <T> ListBuffer<T> lb() {
        return new ListBuffer<T>();
    }

    /**
     * 
     * @param x
     * @return 返回一个包含对象x的ListBuffer<T>实例
     */
    public static <T> ListBuffer<T> of(T x) {
        ListBuffer<T> lb = new ListBuffer<T>();
        lb.add(x);
        return lb;
    }

    /** The list of elements of this buffer.
     * buffer的链表元素
     */
    public List<A> elems;

    /** A pointer pointing to the last, sentinel element of `elems'.
     * buffer结尾元素的哨兵
     */
    public List<A> last;

    /** The number of element in this buffer.
     */
    public int count;

    /** Has a list been created from this buffer yet?
     * 是否调用了ListBuffer<A>.toList()? 相当于把elems共享了出去
     */
    public boolean shared;

    /** Create a new initially empty list buffer.
     * 创建一个空的buffer
     */
    public ListBuffer() {
        clear();
    }

    public final void clear() {
        this.elems = new List<A>(null,null);
        this.last = this.elems;
        count = 0;
        shared = false;
    }

    /** Return the number of elements in this buffer.
     */
    public int length() {
        return count;
    }
    public int size() {
        return count;
    }

    /** Is buffer empty?
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Is buffer not empty?
     */
    public boolean nonEmpty() {
        return count != 0;
    }

    /** Copy list and sets last.
     * 如果已经把this.elems共享了出去(shared=true)，
     * this.append()需要调用copy()方法去复制一份当前的元素来
     */
    private void copy() {
        // GoodCode 复制链表的算法
        List<A> p = elems = new List<A>(elems.head, elems.tail);
        while (true) {
            List<A> tail = p.tail;
            if (tail == null) break;
            tail = new List<A>(tail.head, tail.tail);
            p.setTail(tail);
            p = tail;
        }
        last = p;
        shared = false;
    }

    /** Prepend an element to buffer.
     * 向前追加元素
     */
    public ListBuffer<A> prepend(A x) {
        elems = elems.prepend(x);
        count++;
        return this;
    }

    /** Append an element to buffer.
     * 向后追加元素
     */
    public ListBuffer<A> append(A x) {
        x.getClass(); // null check
        if (shared) copy();
        last.head = x;
        last.setTail(new List<A>(null,null));
        last = last.tail;
        count++;
        return this;
    }

    /** Append all elements in a list to buffer.
     */
    public ListBuffer<A> appendList(List<A> xs) {
        while (xs.nonEmpty()) {
            append(xs.head);
            xs = xs.tail;
        }
        return this;
    }

    /** Append all elements in a list to buffer.
     */
    public ListBuffer<A> appendList(ListBuffer<A> xs) {
        return appendList(xs.toList());
    }

    /** Append all elements in an array to buffer.
     */
    public ListBuffer<A> appendArray(A[] xs) {
        for (int i = 0; i < xs.length; i++) {
            append(xs[i]);
        }
        return this;
    }

    /** Convert buffer to a list of all its elements.
     * 相当于把elems共享了出去
     */
    public List<A> toList() {
        shared = true;
        return elems;
    }

    /** Does the list contain the specified element?
     */
    public boolean contains(Object x) {
        return elems.contains(x);
    }

    /** Convert buffer to an array
     */
    public <T> T[] toArray(T[] vec) {
        return elems.toArray(vec);
    }
    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    /** The first element in this buffer.
     */
    public A first() {
        return elems.head;
    }

    /** Return first element in this buffer and remove
     * 返回buffer中的第一个元素并且将这个元素从buffer中删除
     */
    public A next() {
        A x = elems.head;
        if (elems != last) {
            elems = elems.tail;
            count--;
        }
        return x;
    }

    /** An enumeration of all elements in this buffer.
     * buffer的迭代器，注意这次next()并不会删除元素
     */
    public Iterator<A> iterator() {
        return new Iterator<A>() {
            List<A> elems = ListBuffer.this.elems;
            public boolean hasNext() {
                return elems != last;
            }
            public A next() {
                if (elems == last)
                    throw new NoSuchElementException();
                A elem = elems.head;
                elems = elems.tail;
                return elem;
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public boolean add(A a) {
        append(a);
        return true;
    }

    /**
     * 不支持删除元素，会抛出异常
     */
    public boolean remove(Object o) {
        // GoodCode 以抛出异常的方式来表示不支持此方法
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> c) {
        for (Object x: c) {
            if (!contains(x))
                return false;
        }
        return true;
    }

    public boolean addAll(Collection<? extends A> c) {
        for (A a: c)
            append(a);
        return true;
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持
     */
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * 队列相关方法
     * Inserts the specified element into this queue if it is possible to do
     * so immediately without violating capacity restrictions.
     * When using a capacity-restricted queue, this method is generally
     * preferable to {@link #add}, which can fail to insert an element only
     * by throwing an exception
     */
    public boolean offer(A a) {
        append(a);
        return true;
    }

    /**
     * 队列相关方法
     * 弹出头元素(并将该元素从队列中删除)
     * Retrieves and removes the head of this queue,
     * or returns <tt>null</tt> if this queue is empty.
     */
    public A poll() {
        return next();
    }

    /**
     * 队列相关方法
     * 返回队列头元素(并不将该元素从队列删除)
     * Retrieves, but does not remove, the head of this queue,
     * or returns <tt>null</tt> if this queue is empty.
     */
    public A peek() {
        return first();
    }
}
