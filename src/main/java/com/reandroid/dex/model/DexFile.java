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
package com.reandroid.dex.model;

import com.reandroid.archive.PathTree;
import com.reandroid.arsc.container.FixedBlockContainer;
import com.reandroid.arsc.group.ItemGroup;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.common.BytesOutputStream;
import com.reandroid.dex.header.DexHeader;
import com.reandroid.dex.index.ClassId;
import com.reandroid.dex.item.StringData;
import com.reandroid.dex.index.TypeId;
import com.reandroid.dex.item.AnnotationElement;
import com.reandroid.dex.item.AnnotationItem;
import com.reandroid.dex.sections.Section;
import com.reandroid.dex.sections.SectionList;
import com.reandroid.dex.sections.SectionType;
import com.reandroid.dex.value.ArrayValue;
import com.reandroid.dex.value.DexValue;
import com.reandroid.dex.value.StringValue;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DexFile extends FixedBlockContainer {

    private final SectionList sectionList;
    private Map<String, DexClass> dexClasses = new HashMap<>();
    private PathTree<StringData> pathTree;

    public DexFile() {
        super(1);
        this.sectionList = new SectionList();
        addChild(0, sectionList);
    }
    public void linkTypeSignature(){
        Section<AnnotationItem> annotationSection = getSectionList().get(SectionType.ANNOTATION);
        if(annotationSection == null){
            return;
        }
        ItemGroup<AnnotationItem> group = annotationSection.getPool().getGroup(ANNOTATION_SIG_KEY);
        if(group == null){
            return;
        }
        for(AnnotationItem item : group){
            AnnotationElement element = item.getElement(0);
            if(element == null){
                continue;
            }
            DexValue<?> dexValue = element.getValue();
            if(!(dexValue instanceof ArrayValue)){
                continue;
            }
            ArrayValue arrayValue = (ArrayValue) dexValue;
            linkTypeSignature(arrayValue);
        }
    }
    private void linkTypeSignature(ArrayValue arrayValue){
        for(DexValue<?> value : arrayValue){
            if(!(value instanceof StringValue)){
                continue;
            }
            StringData stringData = ((StringValue) value).getStringData();
            if(stringData != null){
                stringData.addStringUsage(StringData.USAGE_TYPE);
            }
        }
    }
    public void decode(File outDir) throws IOException {
        int size = dexClasses.size();
        System.out.println("Total: " + size);
        int i = 0;
        for(DexClass dexClass : dexClasses.values()){
            i++;
            System.out.println(i + "/" + size + ": " + dexClass);
            dexClass.decode(outDir);
        }
        System.out.println("Done: " + outDir);
    }

    public SectionList getSectionList(){
        return sectionList;
    }
    public DexHeader getHeader() {
        return getSectionList().getHeader();
    }
    private void mapClasses(){
        Section<ClassId> sectionClass = sectionList.get(SectionType.CLASS_ID);
        int count = sectionClass.getCount();
        Map<String, DexClass> dexClasses = new HashMap<>(count);
        this.dexClasses = dexClasses;
        for(int i = 0; i < count; i++){
            DexClass dexClass = DexClass.create(sectionClass.get(i));
            dexClasses.put(dexClass.getName(), dexClass);
        }
    }
    private void buildPathTree(){
        PathTree<StringData> pathTree = PathTree.newRoot();
        Section<TypeId> typeSection = getSectionList().get(SectionType.TYPE_ID);
        Iterator<TypeId> iterator = typeSection.iterator();
        while (iterator.hasNext()){
            TypeId typeId = iterator.next();
            StringData stringData = typeId.getNameData();
            String name = stringData.getString();
            pathTree.add(name, stringData);
        }
        this.pathTree = pathTree;
    }

    @Override
    protected void onPreRefresh() {
        sectionList.refresh();
    }
    @Override
    protected void onRefreshed() {
        sectionList.updateHeader();
    }

    @Override
    public void onReadBytes(BlockReader reader) throws IOException{
        super.onReadBytes(reader);
        //buildPathTree();
    }
    @Override
    public byte[] getBytes(){
        BytesOutputStream outputStream = new BytesOutputStream(
                getHeader().fileSize.get());
        try {
            writeBytes(outputStream);
            outputStream.close();
        } catch (IOException ignored) {
        }
        return outputStream.toByteArray();
    }

    public void read(byte[] dexBytes) throws IOException {
        BlockReader reader = new BlockReader(dexBytes);
        readBytes(reader);
        reader.close();
    }
    public void read(InputStream inputStream) throws IOException {
        BlockReader reader = new BlockReader(inputStream);
        readBytes(reader);
        reader.close();
    }
    public void read(File file) throws IOException {
        BlockReader reader = new BlockReader(file);
        readBytes(reader);
        reader.close();
    }
    public void write(File file) throws IOException {
        File dir = file.getParentFile();
        if(dir != null && !dir.exists()){
            dir.mkdirs();
        }
        FileOutputStream outputStream = new FileOutputStream(file);
        writeBytes(outputStream);
        outputStream.close();
    }
    public static boolean isDexFile(File file){
        if(file == null || !file.isFile()){
            return false;
        }
        DexHeader dexHeader = null;
        try {
            InputStream inputStream = new FileInputStream(file);
            dexHeader = DexHeader.readHeader(inputStream);
            inputStream.close();
        } catch (IOException ignored) {
        }
        return isDexFile(dexHeader);
    }
    public static boolean isDexFile(InputStream inputStream){
        DexHeader dexHeader = null;
        try {
            dexHeader = DexHeader.readHeader(inputStream);
            inputStream.close();
        } catch (IOException ignored) {
        }
        return isDexFile(dexHeader);
    }
    private static boolean isDexFile(DexHeader dexHeader){
        if(dexHeader == null){
            return false;
        }
        if(dexHeader.magic.isDefault()){
            return false;
        }
        int version = dexHeader.version.getVersionAsInteger();
        return version > 0 && version < 1000;
    }

    public static final String ANNOTATION_SIG_KEY = "Ldalvik/annotation/Signature;->value()";
}