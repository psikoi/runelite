/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.loottracker;

import com.google.common.base.Strings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.StackFormatter;

@Getter
class LootTrackerLog extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;

	private final JPanel itemContainer = new JPanel();
	private final JLabel priceLabel = new JLabel();

	private final ItemManager itemManager;

	private long totalPrice;
	private int totalKills;

	@Getter
	private String title;

	private List<LootTrackerEntry> entries = new ArrayList<>();

	LootTrackerLog(final ItemManager itemManager, LootTrackerEntry entry)
	{
		this.title = entry.getTitle();
		this.itemManager = itemManager;

		setLayout(new BorderLayout(0, 1));
		setBorder(new EmptyBorder(5, 0, 0, 0));

		final JPanel logTitle = new JPanel(new BorderLayout(5, 0));
		logTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
		logTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		final JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(Color.WHITE);

		logTitle.add(titleLabel, BorderLayout.WEST);

		// If we have subtitle, add it
		if (!Strings.isNullOrEmpty(entry.getSubTitle()))
		{
			final JLabel subTitleLabel = new JLabel(entry.getSubTitle());
			subTitleLabel.setFont(FontManager.getRunescapeSmallFont());
			subTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			logTitle.add(subTitleLabel, BorderLayout.CENTER);
		}

		priceLabel.setFont(FontManager.getRunescapeSmallFont());
		priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		logTitle.add(priceLabel, BorderLayout.EAST);

		addEntry(entry);

		add(logTitle, BorderLayout.NORTH);
		add(itemContainer, BorderLayout.CENTER);
	}

	void addEntry(LootTrackerEntry entry)
	{
		entries.add(entry);
		totalKills++;
		totalPrice = calculatePrice();

		rebuildItems();

		if (totalPrice > 0)
		{
			priceLabel.setText(StackFormatter.quantityToStackSize(totalPrice) + " gp");
		}
	}

	private void rebuildItems()
	{
		List<LootTrackerItemEntry> items = getCombinedItems();

		// Calculates how many rows need to be display to fit all items
		final int rowSize = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;

		itemContainer.removeAll();
		itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));

		for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
		{
			final JPanel slotContainer = new JPanel();
			slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			if (i < items.size())
			{
				final LootTrackerItemEntry item = items.get(i);
				final JLabel imageLabel = new JLabel();
				imageLabel.setToolTipText(buildToolTip(item));
				imageLabel.setVerticalAlignment(SwingConstants.CENTER);
				imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
				itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(imageLabel);
				slotContainer.add(imageLabel);
			}

			itemContainer.add(slotContainer);
		}

		itemContainer.repaint();
	}

	private List<LootTrackerItemEntry> getCombinedItems()
	{
		final List<LootTrackerItemEntry> list = new ArrayList<>();

		for (final LootTrackerItemEntry entry : getAllItems())
		{
			int quantity = 0;
			for (final LootTrackerItemEntry i : list)
			{
				if (i.getId() == entry.getId())
				{
					quantity = i.getQuantity();
					list.remove(i);
					break;
				}
			}
			if (quantity > 0)
			{
				int newQuantity = entry.getQuantity() + quantity;
				long pricePerItem = entry.getPrice() == 0 ? 0 : (entry.getPrice() / entry.getQuantity());

				list.add(new LootTrackerItemEntry(entry.getId(), entry.getName(), newQuantity, pricePerItem * newQuantity));
			}
			else
			{
				list.add(entry);
			}
		}

		list.sort((i1, i2) -> i1.getPrice() < i2.getPrice() ? 1 : -1);

		return list;
	}

	private List<LootTrackerItemEntry> getAllItems()
	{
		List<LootTrackerItemEntry> list = new ArrayList<>();

		for (LootTrackerEntry entry : entries)
		{
			list.addAll(Arrays.asList(entry.getItems()));
		}
		return list;
	}

	private String buildToolTip(LootTrackerItemEntry item)
	{
		final String name = item.getName();
		final int quantity = item.getQuantity();
		final long price = item.getPrice();

		return name + " x " + quantity + " (" + StackFormatter.quantityToStackSize(price) + ")";
	}

	private long calculatePrice()
	{
		long total = 0;
		for (LootTrackerItemEntry item : getAllItems())
		{
			total += item.getPrice();
		}
		return total;
	}
}
