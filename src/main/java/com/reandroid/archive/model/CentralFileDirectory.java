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
package com.reandroid.archive.model;

import com.reandroid.archive.block.*;
import com.reandroid.archive.io.ZipInput;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CentralFileDirectory {
    private final List<CentralEntryHeader> headerList;
    private EndRecord endRecord;
    private SignatureFooter signatureFooter;
    public CentralFileDirectory(){
        this.headerList = new ArrayList<>();
    }
    public CentralEntryHeader get(LocalFileHeader lfh){
        String name = lfh.getFileName();
        CentralEntryHeader ceh = get(lfh.getIndex());
        if(ceh!=null && Objects.equals(ceh.getFileName() , name)){
            return ceh;
        }
        return get(name);
    }
    public CentralEntryHeader get(String name){
        if(name == null){
            name = "";
        }
        for(CentralEntryHeader ceh:getHeaderList()){
            if(name.equals(ceh.getFileName())){
                return ceh;
            }
        }
        return null;
    }
    public CentralEntryHeader get(int i){
        if(i<0 || i>=headerList.size()){
            return null;
        }
        return headerList.get(i);
    }
    public int count(){
        return headerList.size();
    }
    public List<CentralEntryHeader> getHeaderList() {
        return headerList;
    }

    public SignatureFooter getSignatureFooter() {
        return signatureFooter;
    }
    public EndRecord getEndRecord() {
        return endRecord;
    }
    public void visit(ZipInput zipInput) throws IOException {
        EndRecord endRecord = new EndRecord();
        endRecord.findEndRecord(zipInput);
        InputStream inputStream = zipInput.getInputStream(endRecord.getOffsetOfCentralDirectory(),
                endRecord.getLengthOfCentralDirectory());
        this.endRecord = endRecord;
        loadCentralFileHeaders(inputStream);
        this.signatureFooter = tryFindSignatureFooter(zipInput, endRecord);
    }
    private void loadCentralFileHeaders(InputStream inputStream) throws IOException {
        List<CentralEntryHeader> headerList = this.headerList;
        CentralEntryHeader ceh = new CentralEntryHeader();
        ceh.readBytes(inputStream);
        while (ceh.isValidSignature()){
            headerList.add(ceh);
            ceh = new CentralEntryHeader();
            ceh.readBytes(inputStream);
        }
        inputStream.close();
    }
    private SignatureFooter tryFindSignatureFooter(ZipInput zipInput, EndRecord endRecord) throws IOException {
        long lenCd = endRecord.getLengthOfCentralDirectory();
        int endLength = endRecord.getTotalBytesCount();
        int length = SignatureFooter.MIN_SIZE;
        long offset = zipInput.getLength() - endLength - lenCd - length;
        if(offset < 0){
            return null;
        }
        InputStream inputStream = zipInput.getInputStream(offset, length);
        SignatureFooter signatureFooter = new SignatureFooter();
        signatureFooter.readBytes(inputStream);
        inputStream.close();
        if(signatureFooter.isValid()){
            return signatureFooter;
        }
        return null;
    }
}