/*
 * cyborg-core is an extendable  module based framework for Android.
 *
 * Copyright (C) 2018  Adam van der Kruk aka TacB0sS
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

package com.nu.art.cyborg.core;

import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.nu.art.belog.Logger;
import com.nu.art.core.interfaces.Getter;
import com.nu.art.cyborg.core.abs.Cyborg;
import com.nu.art.cyborg.core.consts.LifecycleState;
import com.nu.art.cyborg.core.dataModels.DataModel;
import com.nu.art.cyborg.errorMessages.ExceptionGenerator;
import com.nu.art.reflection.tools.ReflectiveTools;

import java.lang.reflect.Modifier;

import static com.nu.art.cyborg.core.abs._DebugFlags.Debug_Performance;

@SuppressWarnings("unchecked")
public class CyborgAdapter<Item>
	extends Logger {

	private final Class<? extends ItemRenderer<? extends Item>>[] renderersTypes;

	private final CyborgController controller;

	private final Cyborg cyborg;

	//	private HashMap<Item, ItemRenderer<? extends Item>> renderers = new HashMap<>();

	private DataModel<Item> dataModel;

	private Getter<? extends DataModel<Item>> resolver;

	private CyborgRecyclerAdapter recyclerAdapter;

	private CyborgArrayAdapter arrayAdapter;

	private CyborgPagerAdapter pagerAdapter;

	private boolean autoAnimate;

	@SafeVarargs
	public CyborgAdapter(CyborgController controller, Class<? extends ItemRenderer<? extends Item>>... renderersTypes) {
		this.renderersTypes = renderersTypes;
		this.controller = controller;
		cyborg = CyborgBuilder.getInstance();
	}

	public final void setResolver(Getter<? extends DataModel<Item>> resolver) {
		this.resolver = resolver;
		invalidateDataModel();
	}

	public final void setAutoAnimate(boolean autoAnimate) {
		this.autoAnimate = autoAnimate;
		if (recyclerAdapter != null)
			recyclerAdapter.setHasStableIds(autoAnimate);
	}

	public final void invalidateDataModel() {
		DataModel<Item> dataModel = resolver.get();
		if (dataModel.getItemTypesCount() != renderersTypes.length)
			throw ExceptionGenerator.recyclerMismatchBetweenRendererTypesAndItemsTypesCounts(controller, dataModel.getItemTypes(), renderersTypes);

		setDataModel(dataModel);
		dataModel.notifyDataSetChanged();
	}

	/**
	 * Use the setResolver for better development experience...
	 *
	 * @param dataModel
	 */
	@Deprecated
	public final void setDataModel(DataModel<Item> dataModel) {
		this.dataModel = dataModel;
		this.dataModel.setAdapter(this);
		onDataSetChanged();
	}

	private ItemRenderer<? extends Item> createRendererForType(ViewGroup parent, int typeIndex) {
		ItemRenderer<? extends Item> renderer = instantiateItemRendererType(typeIndex);
		if (Debug_Performance.isEnabled())
			logVerbose("setActivityBridge");
		renderer.setActivityBridge(controller.activityBridge);

		try {
			if (Debug_Performance.isEnabled())
				logVerbose("_createView");
			renderer._createView(LayoutInflater.from(parent.getContext()), parent, false);
		} catch (Throwable e) {
			while (e.getCause() != null) {
				e = e.getCause();
			}
			Log.e("CYBORG", "As Android's exception handling is crappy, here is the reason for the failure: ", e);
			//noinspection ConstantConditions
			throw (RuntimeException) e;
		}

		if (Debug_Performance.isEnabled())
			logVerbose("extractMembersImpl");
		renderer.extractMembersImpl();

		if (Debug_Performance.isEnabled())
			logVerbose("setupRenderer");
		setupRenderer(renderer);

		if (Debug_Performance.isEnabled())
			logVerbose("return");
		return renderer;
	}

	protected ItemRenderer<? extends Item> instantiateItemRendererType(int typeIndex) {
		return instantiateItemRendererType(renderersTypes[typeIndex]);
	}

	protected void setupRenderer(ItemRenderer<? extends Item> renderer) {}

	protected <RendererType extends ItemRenderer<? extends Item>> RendererType instantiateItemRendererType(Class<RendererType> rendererType) {
		Class<?> enclosingClass = rendererType.getEnclosingClass();
		if (enclosingClass == null)
			return ReflectiveTools.newInstance(rendererType);

		if (!enclosingClass.isAssignableFrom(controller.getClass()))
			return ReflectiveTools.newInstance(rendererType);

		if (Modifier.isStatic(rendererType.getModifiers()))
			return ReflectiveTools.newInstance(rendererType);

		return ReflectiveTools.newInstance(rendererType, enclosingClass, controller);
	}

	protected int getItemTypeIndexByPosition(int position) {
		return dataModel.getItemTypeByPosition(position);
	}

	public Item getItemForPosition(int position) {
		return dataModel.getItemForPosition(position);
	}

	public int getPositionForItem(Item item) {
		return dataModel.getPositionForItem(item);
	}

	public int getRealItemsCount(Item item) {
		return dataModel.getRealItemsCount();
	}

	public CyborgRecyclerAdapter getRecyclerAdapter(CyborgRecycler cyborgRecycler) {
		this.recyclerAdapter = new CyborgRecyclerAdapter(cyborgRecycler);
		this.recyclerAdapter.setHasStableIds(this.autoAnimate);
		return this.recyclerAdapter;
	}

	private int getItemsCount() {
		return dataModel == null ? 0 : dataModel.getItemsCount();
	}

	protected void disposeItem(Item itemToDispose) {}

	@SuppressWarnings("unchecked")
	private <ItemToRender extends Item> void _renderItem(ItemRenderer<? extends Item> renderer, int position) {
		Item item = getItemForPosition(position);
		renderItem((ItemRenderer<ItemToRender>) renderer, (ItemToRender) item);
	}

	private <ItemToRender extends Item> void renderItem(ItemRenderer<ItemToRender> renderer, ItemToRender item) {
		ItemToRender previousItem = renderer.getItem();
		if (previousItem != null && previousItem != item) {
			/*	TODO: understand why RecyclerView returns an already bounded ItemRenderer BEFORE the renderer's
				view gets scrolled out of the view, preventing from concluding really whether or not we can dispose of the item*/
			//			disposeItem(previousItem);
			//			renderers.remove(previousItem);
		}
		//		renderers.remove(item);
		//		renderers.put(item, renderer);
		renderer._setItem(item);
		renderer.render();
	}

	//	public final ItemRenderer<? extends Item> getRendererForItem(Item item) {
	//		return renderers.get(item);
	//	}

	public void onDataSetChanged() {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyDataSetChanged();
		android_DataSetChanged();
	}

	private void android_DataSetChanged() {
		if (arrayAdapter != null)
			arrayAdapter.notifyDataSetChanged();

		if (pagerAdapter != null)
			pagerAdapter.notifyDataSetChanged();
	}

	public void onItemRangeInserted(int from, int to) {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyItemRangeChanged(from, to);
		android_DataSetChanged();
	}

	public void onItemMoved(int from, int to) {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyItemMoved(from, to);
		android_DataSetChanged();
	}

	public void onItemRangeRemoved(int from, int to) {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyItemRangeRemoved(from, to);
		android_DataSetChanged();
	}

	public void onItemRemoved(int position) {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyItemRemoved(position);
		android_DataSetChanged();
	}

	public void onItemAtPositionChanged(int position) {
		cyborg.assertMainThread();
		if (recyclerAdapter != null)
			recyclerAdapter.notifyItemChanged(position);
		android_DataSetChanged();
	}

	public final CyborgArrayAdapter getArrayAdapter() {
		if (arrayAdapter != null)
			return arrayAdapter;

		return arrayAdapter = new CyborgArrayAdapter();
	}

	public final PagerAdapter getPagerAdapter() {
		if (pagerAdapter != null)
			return pagerAdapter;

		return pagerAdapter = new CyborgPagerAdapter();
	}

	private void callRendererLifeCycle(ItemRenderer<? extends Item> renderer) {
		renderer.dispatchLifeCycleEvent(LifecycleState.OnCreate);
		renderer.dispatchLifeCycleEvent(LifecycleState.OnResume);
	}

	private class CyborgPagerAdapter
		extends PagerAdapter {

		@Override
		public final View instantiateItem(ViewGroup parent, int position) {
			int viewType = getItemViewType(position);
			ItemRenderer<? extends Item> renderer = createRendererForType(parent, viewType);
			setItem(renderer, position);
			callRendererLifeCycle(renderer);
			parent.addView(renderer.getRootView());
			_renderItem(renderer, position);
			return renderer.getRootView();
		}

		@SuppressWarnings("unchecked")
		private <ItemToRender extends Item> void setItem(ItemRenderer<ItemToRender> renderer, final int position) {
			renderer.setPositionResolver(createPositionResolver(position));
			ItemToRender item = (ItemToRender) getItemForPosition(position);
			renderer._setItem(item);
		}

		public int getItemViewType(int position) {
			return getItemTypeIndexByPosition(position);
		}

		@Override
		public final void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
		}

		@Override
		public final void destroyItem(View collection, int position, Object _view) {
			View view = (View) _view;
			if (view.getParent() != null) {
				((ViewGroup) view.getParent()).removeView(view);
			}
			Item item;

			ItemRenderer<Item> renderer = (ItemRenderer<Item>) ((View) _view).getTag();
			item = renderer.getItem();

			logDebug("Destroy ViewPager Item: " + item);
			//			renderers.remove(item);
			disposeItem(item);
		}

		@Override
		public int getCount() {
			return getItemsCount();
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

		@SuppressWarnings("unchecked")
		public int getItemPosition(Object object) {
			ItemRenderer<Item> renderer = (ItemRenderer<Item>) ((View) object).getTag();
			if (renderer == null)
				return POSITION_NONE;

			int index = dataModel.getPositionForItem(renderer.getItem());
			if (index == -1)
				return POSITION_NONE;

			return index;
		}
	}

	@NonNull
	private PositionResolver createPositionResolver(final int position) {
		return new PositionResolver() {
			@Override
			public int getItemPosition() {
				return position;
			}
		};
	}

	private class CyborgArrayAdapter
		extends ArrayAdapter {

		public CyborgArrayAdapter() {
			super(controller.getActivity(), -1);
		}

		@Override
		public int getCount() {
			return getItemsCount();
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public int getItemViewType(int position) {
			return getItemTypeIndexByPosition(position);
		}

		@Override
		public Item getItem(int position) {
			return getItemForPosition(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			return getView(position, convertView, parent);
		}

		//		@Override
		//		public long getItemId(int position) {
		//			return autoAnimate ? getItemForPosition(position).hashCode() : position;
		//		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			int viewType = getItemViewType(position);
			ItemRenderer<? extends Item> renderer = null;
			if (convertView != null)
				renderer = (ItemRenderer<? extends Item>) convertView.getTag();

			if (renderer == null || renderer.getClass() != renderersTypes[viewType]) {
				renderer = createRendererForType(parent, viewType);
				callRendererLifeCycle(renderer);
			}

			renderer.setPositionResolver(createPositionResolver(position));
			_renderItem(renderer, position);
			return renderer.getRootView();
		}
	}

	private static class CyborgDefaultHolder<T>
		extends ViewHolder
		implements PositionResolver {

		private ItemRenderer<? extends T> renderer;

		public CyborgDefaultHolder(ItemRenderer<? extends T> renderer) {
			super(renderer.getRootView());
			this.renderer = renderer;
			this.renderer.setPositionResolver(this);
		}

		@Override
		public int getItemPosition() {
			return getAdapterPosition();
		}
	}

	public final class CyborgRecyclerAdapter
		extends Adapter<CyborgDefaultHolder>
		implements OnClickListener, OnLongClickListener {

		private long longClickTimeStamp;

		private long clickTimeStamp;

		private final CyborgRecycler parentView;

		@Override
		@SuppressWarnings("unchecked")
		public void onClick(View view) {
			if (parentView.recyclerItemListener == null)
				return;

			if (System.currentTimeMillis() - clickTimeStamp < 500)
				return;
			clickTimeStamp = System.currentTimeMillis();

			ItemRenderer<? extends Item> renderer = (ItemRenderer<? extends Item>) view.getTag();
			parentView.recyclerItemListener.onRecyclerItemClicked(parentView, view, renderer.getItemPosition());
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean onLongClick(View view) {
			if (parentView.recyclerItemLongListener == null)
				return false;

			if (System.currentTimeMillis() - longClickTimeStamp < 500)
				return false;
			longClickTimeStamp = System.currentTimeMillis();

			ItemRenderer<? extends Item> renderer = (ItemRenderer<? extends Item>) view.getTag();
			return parentView.recyclerItemLongListener.onRecyclerItemLongClicked(parentView, view, renderer.getItemPosition());
		}

		public boolean isAutoAnimate() {
			return autoAnimate;
		}

		public CyborgRecyclerAdapter(CyborgRecycler parentView) {
			this.parentView = parentView;
		}

		@Override
		public CyborgDefaultHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			ItemRenderer<? extends Item> renderer = createRendererForType(parent, viewType);

			renderer.getRootView().setOnClickListener(this);
			renderer.getRootView().setOnLongClickListener(this);

			callRendererLifeCycle(renderer);
			return new CyborgDefaultHolder(renderer);
		}

		@Override
		public int getItemViewType(int position) {
			return getItemTypeIndexByPosition(position);
		}

		@Override
		public void onBindViewHolder(CyborgDefaultHolder holder, int position) {
			_renderItem(holder.renderer, position);
		}

		@Override
		public long getItemId(int position) {
			return autoAnimate ? getItemForPosition(position).hashCode() : super.getItemId(position);
		}

		@Override
		public int getItemCount() {
			return getItemsCount();
		}

		//		public View getViewForPosition(int position) {
		//			return renderers.get(getItemForPosition(position)).getRootView();
		//		}

		void invalidateDataModel() {
			CyborgAdapter.this.invalidateDataModel();
		}
	}

	public interface PositionResolver {

		int getItemPosition();
	}

	public static class TouchHelper
		extends SimpleCallback {

		private final ItemTouchHelper helper;

		public TouchHelper(int dragDirs, int swipeDirs) {
			super(dragDirs, swipeDirs);
			helper = new ItemTouchHelper(this);
		}

		public TouchHelper() {
			this(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
		}

		public <T extends ItemRenderer> T getRendererFromHolder(ViewHolder viewHolder) {
			return (T) ((CyborgDefaultHolder) viewHolder).renderer;
		}

		@Override
		public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target) {
			return true;
		}

		@Override
		public void onSwiped(ViewHolder viewHolder, int direction) {
		}

		@Override
		public boolean isLongPressDragEnabled() {
			return false;
		}

		@Override
		public boolean isItemViewSwipeEnabled() {
			return false;
		}

		public final void attachToRecyclerView(RecyclerView recyclerView) {
			helper.attachToRecyclerView(recyclerView);
		}

		public final void startDrag(ViewHolder holder) {
			helper.startDrag(holder);
		}
	}
}
