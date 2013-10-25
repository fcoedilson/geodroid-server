package org.geodroid.server;

import static org.geodroid.server.GeodroidServer.TAG;

import java.io.IOException;     
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jeo.data.DataRef;
import org.jeo.data.Dataset;
import org.jeo.data.Registry;
import org.jeo.data.TileDataset;
import org.jeo.data.VectorDataset;
import org.jeo.data.Workspace;
import org.jeo.feature.Field;
import org.jeo.feature.Schema;
import org.jeo.geom.Geom;
import org.jeo.util.Pair;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.vividsolutions.jts.geom.Geometry;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;

public class LayersFragment extends DetailFragment {

    static enum Tag {
        ALL, POINT, LINE, POLY, TILE;
    }

    @Override
    protected void doCreateView(LayoutInflater inflater, ViewGroup container,
            Preferences p, Bundle state) {
        
        View v = inflater.inflate(R.layout.layers, container);

        TabHost tabs = (TabHost) v.findViewById(R.id.layers_tabs);
        tabs.setup();

        ViewGroup tableRoot = (ViewGroup) v.findViewById(R.id.layers_table_root);

        newTab(tabs, Tag.ALL, R.string.all, tableRoot);
        newTab(tabs, Tag.POINT, R.string.point, tableRoot);
        newTab(tabs, Tag.LINE, R.string.linestring, tableRoot);
        newTab(tabs, Tag.POLY, R.string.polygon, tableRoot);
        newTab(tabs, Tag.TILE, R.string.tile, tableRoot);

        TabWidget tw = tabs.getTabWidget();
        for (int i = 0; i < tw.getTabCount(); i++) {
            TextView tv = (TextView) tw.getChildAt(i).findViewById(android.R.id.title);
            tv.setTextColor(getResources().getColorStateList(R.drawable.tab));
        }
    }

    void newTab(TabHost tabs, Tag tag, int label, final ViewGroup contentRoot) {
        TabSpec tab = tabs.newTabSpec(tag.name());
        
        //TextView text = new TextView(getActivity());
        //text.setText(label);
        //text.setTextSize(16);
        //text.setTextColor(getResources().getColor(R.color.primary_text_dark));
        tab.setIndicator(getResources().getText(label));
        tab.setContent(new TabContentFactory() {
            @Override
            public View createTabContent(String tag) {
                return doCreateTabContent(tag, contentRoot);
            }
        });
        tabs.addTab(tab);
    }

    @SuppressWarnings("unchecked")
    View doCreateTabContent(String tag, ViewGroup root) {
        root.removeAllViews();

        LayoutInflater inflater = getActivity().getLayoutInflater();
        final TableLayout tbl = (TableLayout) 
            inflater.inflate(R.layout.layers_table, root).findViewById(R.id.layers_table);

        // fire off a background task to process the registry for datasets/layers

        new AsyncTask<Pair<Tag,Registry>, Void, Exception>() {
            @Override
            protected Exception doInBackground(Pair<Tag, Registry>... args) {
                Tag t = args[0].first();
                Registry r = args[0].second();

                Predicate<Dataset> filter = new DatasetType(t);
                
                switch(t) {
                case TILE:
                case ALL:
                    break;
                default:
                    filter = Predicates.and(filter, new VectorType(t));
                }

                try {
                    new DatasetVisitor(filter) {
                        protected void visit(Dataset data, DataRef<?> parent) throws IOException {
    
                            final String name = data.getName();
                            final String title = data.getTitle();
    
                            int t = -1;
    
                            if (data instanceof VectorDataset) {
                                Schema schema = ((VectorDataset) data).schema();
                                Field geom = schema.geometry();
                                if (geom != null) {
                                    switch(Geom.Type.from(geom.getType())) {
                                    case POINT:
                                        t = R.string.point;
                                        break;
                                    case LINESTRING:
                                        t = R.string.linestring;
                                        break;
                                    case POLYGON:
                                        t = R.string.polygon;
                                        break;
                                    case MULTIPOINT:
                                        t = R.string.multipoint;
                                        break;
                                    case MULTILINESTRING:
                                        t = R.string.multilinestring;
                                        break;
                                    case MULTIPOLYGON:
                                        t = R.string.multipolygon;
                                        break;
                                    case GEOMETRYCOLLECTION:
                                        t = R.string.collection;
                                        break;
                                    case GEOMETRY:
                                        t = R.string.geometry;
                                        break;
                                    }
                                }
                                else {
                                    t = R.string.vector;
                                }
                            }
                            else if (data instanceof TileDataset) {
                                t = R.string.tile;
                            }
    
                            final String type = getResources().getString(t);

                            Preferences pref = getPreferences();

                            StringBuilder buf = new StringBuilder("http://localhost:");
                            buf.append(pref.getPort());
                            
                            if (data instanceof TileDataset) {
                                buf.append("/tiles");
                            }
                            else {
                                buf.append("/maps");
                            }

                            if (parent != null) {
                                buf.append("/").append(parent.getName());
                            }

                            buf.append("/").append(data.getName()).append(".html");

                            final String prevLink = buf.toString();
                            getView().post(new Runnable() {
                                public void run() {
                                    createTableRow(name, title, type, prevLink, tbl);
                                }
                            });
                        };
                    }.process(r);
                }
                catch(IOException e) {
                    return e;
                }

               return null;
            }

            protected void onPostExecute(Exception error) {
                
            }
        }.execute(Pair.of(Tag.valueOf(tag), getDataRegistry()));

        return tbl;
    }

    void createTableRow(String name, String title, String type, final String prevLink, TableLayout t) {

        TableRow row = 
            (TableRow) getActivity().getLayoutInflater().inflate(R.layout.layers_table_row, null);

        TextView titleText = (TextView) row.findViewById(R.id.layers_table_title);
        if (title != null) {
            titleText.setText(title);
        }
        else {
            row.removeView(titleText);
        }

        TextView nameText = (TextView) row.findViewById(R.id.layers_table_name);
        nameText.setText(name);

        TextView typeText = (TextView) row.findViewById(R.id.layers_table_type);
        typeText.setText(type);

        ImageView prevImg = (ImageView) row.findViewById(R.id.layers_table_preview);
        prevImg.setClickable(true);
        prevImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(prevLink)));
            }
        });

        row.requestLayout();
        t.addView(row);

        TableRow div = 
            (TableRow) getActivity().getLayoutInflater().inflate(R.layout.layers_table_div, null);
        t.addView(div);
    }

    static class DatasetVisitor {
        Predicate<Dataset> filter;

        public DatasetVisitor(Predicate<Dataset> filter) {
            this.filter = filter;
        }

        protected void visit(Dataset dataset, DataRef<?> ref) throws IOException {
        }

        public void process(Registry reg) throws IOException {
            for (DataRef<?> ref : reg.list()) {
                if (Dataset.class.isAssignableFrom(ref.getType())) {
                    Dataset data = (Dataset) ref.resolve();
                    try {
                        if (filter.apply(data)) {
                            visit(data, null);
                        }
                    }
                    finally {
                        data.close();
                    }
                }
                else if (Workspace.class.isAssignableFrom(ref.getType())){
                    Workspace ws = (Workspace) ref.resolve();
                    try {
                        for (DataRef<Dataset> d : ws.list()) {
                            Dataset data = d.resolve();
                            try {
                                if (filter.apply(data)) {
                                    visit(data, ref);
                                }
                            }
                            finally {
                                data.close();
                            }
                        }
                    }
                    finally {
                        ws.close();
                    }
                }
            }
        }
    }
    
    static class DatasetType implements Predicate<Dataset> {

        Tag tag;
        
        public DatasetType(Tag tag) {
            this.tag = tag;
        }

        @Override
        public boolean apply(Dataset data) {
            switch(tag) {
            case TILE:
                return data instanceof TileDataset;
            default:
                return data instanceof VectorDataset;
            }
        }
    }

    static class VectorType implements Predicate<Dataset> {

        Tag tag;

        public VectorType(Tag tag) {
            this.tag = tag;
        }

        @Override
        public boolean apply(Dataset data) {
            if (data instanceof VectorDataset) {
                VectorDataset vector = (VectorDataset) data;
                Schema schema;
                try {
                    schema = vector.schema();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Field geometry = schema.geometry();
                if (geometry == null) {
                    return false;
                }

                Class<? extends Geometry> gtype = (Class<? extends Geometry>) geometry.getType();
                switch(tag) {
                case POINT:
                    switch(Geom.Type.from(gtype)) {
                        case POINT:
                        case MULTIPOINT:
                            return true;
                        default:
                            return false;
                    }
                case LINE:
                    switch(Geom.Type.from(gtype)) {
                        case LINESTRING:
                        case MULTILINESTRING:
                            return true;
                        default:
                            return false;
                    }
                
                case POLY:
                    switch(Geom.Type.from(gtype)) {
                        case POLYGON:
                        case MULTIPOLYGON:
                            return true;
                        default:
                            return false;
                    }
                case ALL:
                    return true;
                }
            }

            return false;
        }
    
    }
}
