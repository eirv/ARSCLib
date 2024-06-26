/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.dex.sections;

import com.reandroid.dex.base.BlockListArray;
import com.reandroid.dex.base.IntegerPair;
import com.reandroid.dex.common.SectionItem;
import com.reandroid.dex.id.IdItem;
import com.reandroid.dex.key.Key;
import com.reandroid.utils.collection.ComputeIterator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

public class IdSection<T extends IdItem> extends Section<T> {

    public IdSection(IntegerPair countAndOffset, SectionType<T> sectionType) {
        super(sectionType, new IdSectionArray<>(countAndOffset, sectionType.getCreator()));
    }
    IdSection(SectionType<T> sectionType, IdSectionArray<T> itemArray){
        super(sectionType, itemArray);
    }

    public int getFreeSpace() {
        return 0xffff - getCount();
    }

    @SuppressWarnings("unchecked")
    @Override
    boolean keyChanged(SectionItem block, Key key, boolean immediateSort) {
        boolean changed = super.keyChanged(block, key, immediateSort);
        if(!immediateSort){
            return changed;
        }
        T item = (T) block;
        sortImmediate(item);
        return changed;
    }

    @Override
    protected void onPreRefresh() {
        super.onPreRefresh();
        sort();
    }
    @Override
    public boolean remove(Key key){
        T item = getSectionItem(key);
        if(item != null && item.getParent() != null){
            item.removeSelf();
            return true;
        }
        return false;
    }
    @Override
    public boolean removeWithKeys(Predicate<? super Key> filter){
        return getItemArray().removeIf(item -> filter.test(item.getKey()));
    }
    @Override
    public T getSectionItem(int i){
        T result = getItemArray().get(i);
        if(i >= 0 && result == null){
            throw new NullPointerException("Null id: " + i);
        }
        return result;
    }
    @Override
    public T[] getSectionItems(int[] indexes){
        if(indexes == null || indexes.length == 0){
            return null;
        }
        BlockListArray<T> itemArray = getItemArray();
        int length = indexes.length;
        T[] results = itemArray.newArrayInstance(indexes.length);
        for(int i = 0; i < length; i++){
            results[i] = itemArray.get(indexes[i]);
            if(results[i] == null){
                throw new NullPointerException("Null id: " + i);
            }
        }
        return results;
    }
    public T createItem() {
        return getItemArray().createNext();
    }
    @Override
    void onRefreshed(int position){
        position += getItemArray().countBytes();
        updateNextSection(position);
    }

    boolean canAddAll(Collection<IdItem> collection, int reserveSpace) {
        if(reserveSpace < 0) {
            reserveSpace = 200;
        }
        int freeSpace = getFreeSpace();
        if(freeSpace <= reserveSpace) {
            return false;
        }
        if(collection.size() < freeSpace) {
            return true;
        }
        Set<Key> checkedKeys = new HashSet<>();
        SectionType<T> sectionType = getSectionType();
        for (IdItem item : collection) {
            if (item.getSectionType() != sectionType) {
                continue;
            }
            Key key = item.getKey();
            if(checkedKeys.contains(key)) {
                continue;
            }
            checkedKeys.add(key);
            if (!contains(key)) {
                freeSpace --;
                if (freeSpace <= reserveSpace) {
                    return false;
                }
            }
        }
        return true;
    }
}
