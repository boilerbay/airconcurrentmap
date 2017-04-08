// Copyright (C) 1997-2017 Roger L. Deran.
//
//    This file is part of AirConcurrentMap. AirConcurrentMap
//    itself is proprietary software.
//
//    This file is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 2 of the License, or
//    (at your option) any later version.
//
//    This file is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with AirConcurrentMap.  If not, see <http://www.gnu.org/licenses/>.
//
//    For dual licensing, see boilerbay.com.
//    The author email is rlderan2 there.

package com.infinitydb.map.util;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * For collecting results as a matrix.
 * 
 * For example columns can be TestNames and rows can be tested Map sizes and
 * cells that are performance results. The Map sizes can be linear or
 * logarithmic for example in {@link MapVisitorPerformanceTest}.
 * 
 * @author Roger
 */
public class SimpleSamplingMatrix<PrimaryKey, ColumnId extends Enum<ColumnId>, Value> {
    String primaryKeyName;
    Class<ColumnId> columnId;
    String headingFormat;
    String primaryKeyFormat;
    String valueFormat;
    ConcurrentSkipListMap<PrimaryKey, SortedMap<ColumnId, Value>> table =
            new ConcurrentSkipListMap();
    ConcurrentSkipListSet<ColumnId> headingsUsed = new ConcurrentSkipListSet<ColumnId>();

    public SimpleSamplingMatrix(String primaryKeyName, Class<ColumnId> columnId,
            String headingFormat, String primaryKeyFormat, String valueFormat) {
        this.primaryKeyName = primaryKeyName;
        this.columnId = columnId;
        this.headingFormat = headingFormat;
        this.primaryKeyFormat = primaryKeyFormat;
        this.valueFormat = valueFormat;
    }

    public void put(PrimaryKey rowId, ColumnId columnId, Value value) {
        SortedMap<ColumnId, Value> row = table.get(rowId);
        if (row == null) {
            row = new ConcurrentSkipListMap<ColumnId, Value>();
            table.put(rowId, row);
        }
        row.put(columnId, value);
        headingsUsed.add(columnId);
    }

    public Value get(PrimaryKey rowId, ColumnId columnId) {
        SortedMap<ColumnId, Value> row = table.get(rowId);
        if (row == null)
            return null;
        return row.get(columnId);
    }

    public void print() {
        // Print table heading. Each map type of first row
        try {
            PrimaryKey firstKey = table.firstKey();
            System.out.print(primaryKeyName + " ");
            ArrayList<String> headings = new ArrayList<String>();
            ColumnId[] columnIds = columnId.getEnumConstants();
            // SortedMap<ColumnId, Value> firstRow = table.get(firstKey);
            // for (Entry<ColumnId, Value> e : firstRow.entrySet()) {
            // System.out.printf(headingFormat, e.getKey().name());
            // }
            for (ColumnId cid : headingsUsed) {
                System.out.printf(headingFormat, cid.name());
            }
            System.out.println();

            // print the table, each row holding data for each map type
            for (Entry<PrimaryKey, SortedMap<ColumnId, Value>> row : table.entrySet()) {
                ArrayList<Object> values = new ArrayList<Object>();
                String format = "";
                values.add(row.getKey());
                format += primaryKeyFormat;
                for (ColumnId cid : headingsUsed) {
                    Object v = row.getValue().get(cid);
                    values.add(v);
                    format += valueFormat + " ";
                }
                // for (Value v : row.getValue().values()) {
                // values.add(v);
                // format += valueFormat + " ";
                // }
                System.out.printf(format, values.toArray());
                System.out.println();
            }
        } catch (NoSuchElementException e) {
            System.out.println("table empty");
        }
    }
}
