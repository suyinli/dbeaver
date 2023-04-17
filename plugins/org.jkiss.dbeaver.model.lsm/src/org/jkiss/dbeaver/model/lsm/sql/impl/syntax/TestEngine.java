/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.lsm.sql.impl.syntax;

import org.jkiss.dbeaver.model.lsm.LSMAnalysis;
import org.jkiss.dbeaver.model.lsm.LSMAnalysisCase;
import org.jkiss.dbeaver.model.lsm.LSMDialect;
import org.jkiss.dbeaver.model.lsm.LSMSource;
import org.jkiss.dbeaver.model.lsm.sql.LSMSelectStatement;
import org.jkiss.dbeaver.model.lsm.sql.dialect.Sql92Dialect;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutionException;


public class TestEngine {

    public static void main(String[] args) throws Exception {
        try {
            LSMSource source = LSMSource.fromReader(new StringReader("SELECT a, b, c FROM t1 x, t2 y"));
            
            LSMDialect dd = Sql92Dialect.getInstance();
            
            LSMAnalysisCase<LSMSelectStatement, ?> selectStmtAnalysisCase = dd.findAnalysisCase(LSMSelectStatement.class);
            
            LSMAnalysis<LSMSelectStatement> analysis = dd.prepareAnalysis(source, selectStmtAnalysisCase).get();
            
            LSMSelectStatement model = analysis.getModel().get();
            
            System.out.println(model);
        } catch (IOException | InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
        }
    }
}