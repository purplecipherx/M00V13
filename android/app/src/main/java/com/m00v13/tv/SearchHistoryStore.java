package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchHistoryStore {
    private static final String PREFS="m00v13_search_history";
    private static final String KEY="recent";
    private static final int MAX=20;
    private final SharedPreferences p;

    public SearchHistoryStore(Context c){p=c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public List<String> recent(){
        ArrayList<String> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(p.getString(KEY,"[]"));for(int i=0;i<a.length();i++){String q=a.optString(i,"").trim();if(!q.isEmpty())out.add(q);}}catch(Exception ignored){}
        return Collections.unmodifiableList(out);
    }

    public void add(String query){
        String q=query==null?"":query.trim(); if(q.isEmpty())return;
        ArrayList<String> out=new ArrayList<>(); out.add(q);
        for(String existing:recent()) if(!existing.equalsIgnoreCase(q)&&out.size()<MAX)out.add(existing);
        save(out);
    }

    public void remove(String query){
        ArrayList<String> out=new ArrayList<>();
        for(String existing:recent()) if(!existing.equalsIgnoreCase(query))out.add(existing);
        save(out);
    }

    public void clear(){p.edit().remove(KEY).apply();}
    private void save(List<String> values){JSONArray a=new JSONArray();for(String s:values)a.put(s);p.edit().putString(KEY,a.toString()).apply();}
}
