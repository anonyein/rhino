/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * This class holds the various SlotMaps of various types, and knows how to atomically switch
 * between them when we need to so that we use the right data structure at the right time.
 */
class SlotMapContainer extends SlotMapOwner implements SlotMap {

    /**
     * Once the object has this many properties in it, we will replace the EmbeddedSlotMap with
     * HashSlotMap. We can adjust this parameter to balance performance for typical objects versus
     * performance for huge objects with many collisions.
     */
    static final int LARGE_HASH_SIZE = 2000;

    static final int DEFAULT_SIZE = 10;

    private static final class EmptySlotMap implements SlotMap {

        @Override
        public Iterator<Slot> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Slot modify(SlotMapOwner owner, Object key, int index, int attributes) {
            var newSlot = new Slot(key, index, attributes);
            var map = new SingleEntrySlotMap(newSlot);
            owner.setMap(map);
            return newSlot;
        }

        @Override
        public Slot query(Object key, int index) {
            return null;
        }

        @Override
        public void add(SlotMapOwner owner, Slot newSlot) {
            if (newSlot != null) {
                var map = new SingleEntrySlotMap(newSlot);
                owner.setMap(map);
            }
        }

        @Override
        public <S extends Slot> S compute(
                SlotMapOwner owner, Object key, int index, SlotComputer<S> c) {
            var newSlot = c.compute(key, index, null);
            if (newSlot != null) {
                var map = new SingleEntrySlotMap(newSlot);
                owner.setMap(map);
            }
            return newSlot;
        }
    }

    private static final class Iter implements Iterator<Slot> {
        private Slot next;

        Iter(Slot slot) {
            next = slot;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Slot next() {
            Slot ret = next;
            if (ret == null) {
                throw new NoSuchElementException();
            }
            next = next.orderedNext;
            return ret;
        }
    }

    static final class SingleEntrySlotMap implements SlotMap {

        SingleEntrySlotMap(Slot slot) {
            assert (slot != null);
            this.slot = slot;
        }

        private final Slot slot;

        @Override
        public Iterator<Slot> iterator() {
            return new Iter(slot);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Slot modify(SlotMapOwner owner, Object key, int index, int attributes) {
            final int indexOrHash = (key != null ? key.hashCode() : index);

            if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                return slot;
            }
            Slot newSlot = new Slot(key, index, attributes);
            add(owner, newSlot);
            return newSlot;
        }

        @Override
        public Slot query(Object key, int index) {
            final int indexOrHash = (key != null ? key.hashCode() : index);

            if (indexOrHash == slot.indexOrHash && Objects.equals(slot.name, key)) {
                return slot;
            }
            return null;
        }

        @Override
        public void add(SlotMapOwner owner, Slot newSlot) {
            if (owner == null) {
                throw new IllegalStateException();
            } else {
                var newMap = new EmbeddedSlotMap();
                owner.setMap(newMap);
                newMap.add(owner, slot);
                newMap.add(owner, newSlot);
            }
        }

        @Override
        public <S extends Slot> S compute(
                SlotMapOwner owner, Object key, int index, SlotComputer<S> c) {
            var newMap = new EmbeddedSlotMap();
            owner.setMap(newMap);
            newMap.add(owner, slot);
            return newMap.compute(owner, key, index, c);
        }
    }

    static SlotMap EMPTY_SLOT_MAP = new EmptySlotMap();

    public SlotMapContainer() {
        this(DEFAULT_SIZE);
    }

    SlotMapContainer(int initialSize) {
        super(initialMap(initialSize));
    }

    private static SlotMap initialMap(int initialSize) {
        if (initialSize == 0) {
            return EMPTY_SLOT_MAP;
        } else if (initialSize > LARGE_HASH_SIZE) {
            return new HashSlotMap();
        } else {
            return new EmbeddedSlotMap();
        }
    }

    @Override
    public int size() {
        return getMap().size();
    }

    @Override
    public int dirtySize() {
        return getMap().size();
    }

    @Override
    public boolean isEmpty() {
        return getMap().isEmpty();
    }

    @Override
    public Slot modify(SlotMapOwner owner, Object key, int index, int attributes) {
        return getMap().modify(this, key, index, attributes);
    }

    @Override
    public <S extends Slot> S compute(
            SlotMapOwner owner, Object key, int index, SlotComputer<S> c) {
        return getMap().compute(this, key, index, c);
    }

    @Override
    public Slot query(Object key, int index) {
        return getMap().query(key, index);
    }

    @Override
    public void add(SlotMapOwner owner, Slot newSlot) {
        getMap().add(this, newSlot);
    }

    @Override
    public Iterator<Slot> iterator() {
        return getMap().iterator();
    }

    @Override
    public long readLock() {
        // No locking in the default implementation
        return 0L;
    }

    @Override
    public void unlockRead(long stamp) {
        // No locking in the default implementation
    }

    /**
     * Before inserting a new item in the map, check and see if we need to expand from the embedded
     * map to a HashMap that is more robust against large numbers of hash collisions.
     */
    protected void checkMapSize() {
        var map = getMap();
        if (map == EMPTY_SLOT_MAP) {
            setMap(new EmbeddedSlotMap());
        } else if ((map instanceof EmbeddedSlotMap) && map.size() >= LARGE_HASH_SIZE) {
            SlotMap newMap = new HashSlotMap(map);
            setMap(newMap);
        }
    }
}
