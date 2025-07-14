/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.reddeer.core.handler;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.reddeer.common.condition.AbstractWaitCondition;
import org.eclipse.reddeer.common.exception.WaitTimeoutExpiredException;
import org.eclipse.reddeer.common.logging.Logger;
import org.eclipse.reddeer.common.util.Display;
import org.eclipse.reddeer.common.util.ResultRunnable;
import org.eclipse.reddeer.common.wait.TimePeriod;
import org.eclipse.reddeer.common.wait.WaitUntil;
import org.eclipse.reddeer.core.exception.CoreLayerException;

/**
 * Contains methods for handling UI operations on {@link TreeItem} widgets.
 * 
 * @author Lucia Jelinkova
 *
 */
public class TreeItemHandler extends ItemHandler{

	private static final Logger logger = Logger.getLogger(TreeItemHandler.class);
	private static TreeItemHandler instance;
	
	/**
	 * Gets instance of TreeItemHandler.
	 * 
	 * @return instance of TreeItemHandler
	 */
	public static TreeItemHandler getInstance(){
		if(instance == null){
			instance = new TreeItemHandler();
		}
		return instance;
	}

	/**
	 * Gets text from cell of specified {@link TreeItem} on the position
	 * specified by index.
	 * 
	 * @param treeItem
	 *            tree item to handle
	 * @param cellIndex
	 *            index of cell to get text
	 * @return text of the cell
	 */
	public String getText(final TreeItem treeItem, final int cellIndex) {
		String text = Display.syncExec(new ResultRunnable<String>() {
			@Override
			public String run() {
				return treeItem.getText(cellIndex);
			}
		});
		return text;
	}

	/**
	 * Gets tool tip of specified tree item.
	 * 
	 * @param item
	 *            item to handle
	 * @return tool tip text of specified tree item
	 */
	public String getToolTipText(final TreeItem item) {
		String text = Display.syncExec(new ResultRunnable<String>() {
			@Override
			public String run() {
				return item.getParent().getToolTipText();
			}
		});
		return text;
	}

	/**
	 * Finds out whether specified tree item is checked or not.
	 * 
	 * @param item
	 *            item to handle
	 * @return true if specified tree item is expanded, false otherwise
	 */
	public boolean isExpanded(final TreeItem item) {
		return Display.syncExec(new ResultRunnable<Boolean>() {

			@Override
			public Boolean run() {
				return item.getExpanded();
			}
		});
	}

	/**
	 * Sets specified text to column on the position specified by index in
	 * specified tree item.
	 * 
	 * @param treeItem
	 *            tree item to handle
	 * @param cellIndex
	 *            index of cell to set text
	 * @param text
	 *            text to set
	 */
	public void setText(final TreeItem treeItem, final int cellIndex, final String text) {
		Display.syncExec(new Runnable() {

			@Override
			public void run() {
				treeItem.setText(cellIndex, text);
			}
		});
	}

	/**
	 * Selects specified tree items in currently focused tree.
	 *
	 * @param selection
	 *            the selection
	 */
	public void selectItems(final TreeItem... selection) {
		logger.info("Select tree items: ");
		final Tree swtTree = getParent(selection[0]);
		TreeHandler.getInstance().setFocus(swtTree);

		Display.syncExec(new Runnable() {
			public void run() {
				if (!(SWT.MULTI == (swtTree.getStyle() & SWT.MULTI)) && selection.length > 1) {
					throw new CoreLayerException("Tree does not support SWT.MULTI, cannot make multiple selections");
				}
				logger.debug("Set Tree selection");
				swtTree.setSelection(selection);
			}
		});
		TreeHandler.getInstance().notifySelect(swtTree);
		logger.debug("Selected Tree Items:");
		for (TreeItem treeItem : selection) {
			logger.debug("  " + getText(treeItem));
		}
	}

	/**
	 * Selects swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 */
	public void select(final TreeItem swtTreeItem) {
		Display.syncExec(new Runnable() {
			@Override
			public void run() {
				logger.debug("Selecting tree item: " + swtTreeItem.getText());
				swtTreeItem.getParent().setFocus();
				swtTreeItem.getParent().setSelection(swtTreeItem);
			}
		});
		logger.debug("Notify tree item " + getText(swtTreeItem) + " about selection");
		TreeHandler.getInstance().notifyTree(swtTreeItem, TreeHandler.getInstance().createEventForTree(swtTreeItem, SWT.Selection));
		logger.info("Selected tree item: " + getText(swtTreeItem));
	}

	/**
	 * Get child swt tree item with specified text.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @param text
	 *            text of tree item
	 * @return child item of specified tree item
	 */
	public TreeItem getItem(final TreeItem swtTreeItem, final String text) {
		logger.debug("Get child tree item " + text + " of tree item " + getText(swtTreeItem));
		expand(swtTreeItem);
		TreeItem result = Display.syncExec(new ResultRunnable<TreeItem>() {
			@Override
			public TreeItem run() {
				org.eclipse.swt.widgets.TreeItem[] items = swtTreeItem.getItems();
				boolean isFound = false;
				int index = 0;
				while (!isFound && index < items.length) {
					if (items[index].getText().equals(text)) {
						isFound = true;
					} else {
						index++;
					}
				}
				if (!isFound) {
					return null;
				} else {
					return items[index];
				}
			}
		});
		if (result != null) {
			return result;
		} else {
			CoreLayerException exception = new CoreLayerException(
					"Tree Item " + this + " has no Tree Item with text " + text);
			exception.addMessageDetail("Tree Item " + this + " has these direct children:");
			for (TreeItem treeItem : TreeHandler.getInstance().getSWTItems(getParent(swtTreeItem))) {
				exception.addMessageDetail("  " + getText(treeItem, 0));
			}
			throw exception;
		}
	}

	/**
	 * Gets children of specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return tree item children
	 */
	public List<TreeItem> getChildrenItems(final TreeItem swtTreeItem) {
		expand(swtTreeItem, TimePeriod.SHORT);
		return Display.syncExec(new ResultRunnable<List<TreeItem>>() {
			@Override
			public List<TreeItem> run() {
				org.eclipse.swt.widgets.TreeItem[] items = swtTreeItem.getItems();
				return Arrays.asList(items);
			}
		});
	}

	/**
	 * Gets parent of specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return parent tree of specified item
	 */
	public Tree getParent(final TreeItem swtTreeItem) {
		return Display.syncExec(new ResultRunnable<Tree>() {
			@Override
			public Tree run() {
				return swtTreeItem.getParent();
			}
		});
	}
	
	/**
	 * Gets parent item of specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return parent tree item of specified item
	 */
	public TreeItem getParentItem(final TreeItem swtTreeItem){
		return Display.syncExec(new ResultRunnable<TreeItem>() {
			@Override
			public TreeItem run() {
				return swtTreeItem.getParentItem();
			}
		});
	}

	/**
	 * Gets path to specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return path to specified tree item in tree
	 */
	public String[] getPath(final TreeItem swtTreeItem) {
		return Display.syncExec(new ResultRunnable<String[]>() {
			@Override
			public String[] run() {
				org.eclipse.swt.widgets.TreeItem swttiDummy = swtTreeItem;
				LinkedList<String> items = new LinkedList<String>();
				while (swttiDummy != null) {
					items.addFirst(swttiDummy.getText());
					swttiDummy = swttiDummy.getParentItem();
				}
				return items.toArray(new String[0]);
			}
		});
	}

	/**
	 * Finds out whether a specified swt tree item is checked or not.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return true if specified item is checked, false otherwise
	 */
	public boolean isChecked(final org.eclipse.swt.widgets.TreeItem swtTreeItem) {
		return Display.syncExec(new ResultRunnable<Boolean>() {
			@Override
			public Boolean run() {
				return swtTreeItem.getChecked();
			}
		});
	}

	/**
	 * Set or unset check on specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @param check
	 *            check value of specified tree item
	 */
	public void setChecked(final TreeItem swtTreeItem, final boolean check) {
		logger.debug(
				(check ? "Check" : "Uncheck") + "Tree Item " + getText(swtTreeItem) + ":");
		Display.syncExec(new Runnable() {
			@Override
			public void run() {
				swtTreeItem.setChecked(check);
			}
		});
		logger.debug("Notify tree about check event");
		TreeHandler.getInstance().notifyTree(swtTreeItem,TreeHandler.getInstance().createEventForTree(swtTreeItem, SWT.Selection, SWT.CHECK));
		logger.info((check ? "Checked: " : "Unchecked: ") + getText(swtTreeItem));
	}

	/**
	 * Finds out whether a specified swt tree item is selected or not.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @return true if specified item is selected, false otherwise
	 */
	public boolean isSelected(final TreeItem swtTreeItem) {
		return Display.syncExec(new ResultRunnable<Boolean>() {
			@Override
			public Boolean run() {
				return Arrays.asList(swtTreeItem.getParent().getSelection()).contains(swtTreeItem);
			}
		});
	}

	/**
	 * Collapses specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 */
	public void collapse(final TreeItem swtTreeItem) {
		logger.debug("Collapse Tree Item " + getText(swtTreeItem));
		if (isExpanded(swtTreeItem)) {
			Display.syncExec(new Runnable() {
				@Override
				public void run() {
					logger.debug("Setting tree item " + swtTreeItem.getText() + " collapsed");
					swtTreeItem.setExpanded(false);
				}
			});
			logger.debug("Notify tree about collapse event");
			TreeHandler.getInstance().notifyTree(swtTreeItem,TreeHandler.getInstance().createEventForTree(swtTreeItem, SWT.Collapse));
		} else {
			logger.debug("Tree Item " + getText(swtTreeItem)
					+ " is already collapsed. No action performed");
		}
		logger.info("Collapsed: " + getText(swtTreeItem));
	}

	/**
	 * Expands specified swt tree item.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 */
	public void expand(final TreeItem swtTreeItem) {
		expand(swtTreeItem, TimePeriod.SHORT);
	}

	/**
	 * Expands specified swt tree item and wait for it for specified time period.
	 * 
	 * @param swtTreeItem
	 *            tree item to handle
	 * @param timePeriod
	 *            time period to wait for
	 */
	public void expand(final TreeItem swtTreeItem, TimePeriod timePeriod) {
		logger.debug("Expand Tree Item " + getText(swtTreeItem));

		final TreeExpandListener tel = new TreeExpandListener();

		Display.syncExec(new Runnable() {
			@Override
			public void run() {
				swtTreeItem.getParent().addListener(SWT.Expand, tel);
			}
		});

		try {
			new WaitUntil(new TreeHeardExpandNotification(swtTreeItem, tel, false), timePeriod);
		} catch (WaitTimeoutExpiredException ex) {
			new WaitUntil(new TreeHeardExpandNotification(swtTreeItem, tel, true), timePeriod);
		}
		logger.info("Expanded: " + getText(swtTreeItem));

		Display.syncExec(new Runnable() {
			@Override
			public void run() {
				swtTreeItem.setExpanded(true);
				swtTreeItem.getParent().update();
			}
		});
	}

	/**
	 * Clicks on specified TreeItem.
	 *
	 * @param swtTreeItem
	 *            the swt tree item
	 */
	public void click(final TreeItem swtTreeItem) {
		Rectangle bounds = getBounds(swtTreeItem);
		notifyMouseClick(swtTreeItem, bounds.x + (bounds.width / 2), bounds.y + (bounds.height / 2));
	}

	/**
	 * Get bounds of specified TreeItem bounds. TreeItem should be enabled
	 * before calling this method.
	 *
	 * @param swtTreeItem
	 *            the swt tree item
	 * @return tree item bounds
	 */
	public Rectangle getBounds(final TreeItem swtTreeItem) {
		return Display.syncExec(new ResultRunnable<Rectangle>() {
			@Override
			public Rectangle run() {
				return swtTreeItem.getBounds();
			}
		});
	}

	/**
	 * Notifies specified TreeItem about Mouse Click over it
	 * 
	 * @param swtTreeItem
	 * @param x
	 * @param y
	 */
	private void notifyMouseClick(TreeItem swtTreeItem, int x, int y) {
		TreeHandler.getInstance().notifyTree(swtTreeItem, SWT.MouseDown,
				createMouseEvent(swtTreeItem, null, SWT.NONE, x, y, 1, SWT.NONE, 1));
		TreeHandler.getInstance().notifyTree(swtTreeItem, SWT.MouseUp,
				createMouseEvent(swtTreeItem, null, SWT.NONE, x, y, 1, SWT.BUTTON1, 1));
	}

	/**
	 * Creates Mouse Event for TreeItem
	 * 
	 * @param swtTreeItem
	 * @param item
	 * @param type
	 * @param x
	 * @param y
	 * @param button
	 * @param stateMask
	 * @param count
	 * @return
	 */
	private Event createMouseEvent(TreeItem swtTreeItem, Widget item, int type, int x, int y, int button, int stateMask,
			int count) {
		Event event = new Event();
		event.time = (int) System.currentTimeMillis();
		event.widget = swtTreeItem;
		event.display = Display.getDisplay();
		event.x = x;
		event.y = y;
		event.button = button;
		event.stateMask = stateMask;
		event.count = count;
		event.item = item;
		event.type = type;
		return event;
	}

	private class TreeExpandListener implements Listener {

		private boolean heard = false;

		@Override
		public void handleEvent(Event arg0) {
			heard = true;
		}

		public boolean isHeard() {
			return heard;
		}

	}

	private class TreeHeardExpandNotification extends AbstractWaitCondition {

		private org.eclipse.swt.widgets.TreeItem treeItem;
		private TreeExpandListener listener;
		private boolean sync;

		public TreeHeardExpandNotification(org.eclipse.swt.widgets.TreeItem treeItem, TreeExpandListener listener,
				boolean sync) {
			this.treeItem = treeItem;
			this.listener = listener;
			this.sync = sync;
		}

		@Override
		public boolean test() {
			if (!isExpanded(treeItem)) {
				if (sync) {
					TreeHandler.getInstance().notifyTreeSync(treeItem, TreeHandler.getInstance().createEventForTree(treeItem, SWT.Expand));
				} else {
					TreeHandler.getInstance().notifyTree(treeItem, TreeHandler.getInstance().createEventForTree(treeItem, SWT.Expand));
				}
				return listener.isHeard();
			} else {
				logger.debug("Tree Item " + getText(treeItem)
						+ " is already expanded. No action performed");
			}
			return true;
		}

		@Override
		public String description() {
			return "tree heard expand notification";
		}

	}

	/**
	 * Sets focus to tree item
	 * @param swtItem to handle
	 */
	public void setFocus(TreeItem swtItem) {
		new ControlHandler().setFocus(getParent(swtItem));
		
	}
}
