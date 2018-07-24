/*
 * Copyright (c) 2018, TheStonedTurtle <https://github.com/TheStonedTurtle>
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
package net.runelite.client.plugins.loottracker.data;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.http.api.RuneLiteAPI;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.runelite.client.RuneLite.LOOT_RECORD_DIR;

@Slf4j
public class LootRecordWriter
{
	private static final String FILE_EXTENSION = ".log";

	private Client client;
	// Data is stored in a folder with the players in-game username
	private File playerFolder;
	// Record of existing .log files
	private Map<String, File> fileMap = new HashMap<>();

	public LootRecordWriter(Client client)
	{
		this.client = client;

		LOOT_RECORD_DIR.mkdir();

		// Ensure playerFolder is up to date.
		updatePlayerFolder();

		// Create fileMap
		File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".log"));
		for (File f : files)
		{

			fileMap.put(f.getName(), f);
			log.debug("Found log file: {}", f.getName());
		}
	}

	private void updatePlayerFolder()
	{
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			playerFolder = new File(LOOT_RECORD_DIR, client.getLocalPlayer().getName());
		}
		else
		{
			playerFolder = LOOT_RECORD_DIR;
		}

		playerFolder.mkdir();
	}

	private File getOrCreateFile(String fileName)
	{
		File req = fileMap.get(fileName);
		if (req == null)
		{
			req = new File(playerFolder, fileName);
			fileMap.put(fileName, req);
		}

		return req;
	}

	private String npcNameToFileName(String npcName)
	{
		return npcName.toLowerCase().trim() + FILE_EXTENSION;
	}

	private synchronized Collection<LootRecord> loadLootRecords(String npcName)
	{
		String fileName = npcNameToFileName(npcName);
		File file = getOrCreateFile(fileName);

		try (BufferedReader br = new BufferedReader(new FileReader(file)))
		{
			Collection<LootRecord> data = new ArrayList<>();

			String line;
			while ((line = br.readLine()) != null)
			{
				if (line.length() > 0)
				{
					LootRecord r = RuneLiteAPI.GSON.fromJson(line, LootRecord.class);
					data.add(r);
				}
			}

			return data;
		}
		catch (FileNotFoundException e)
		{
			log.debug("File not found: {}", fileName);
			return null;
		}
		catch (IOException e)
		{
			log.warn("IOException for file {}: {}", fileName, e.getMessage());
			return null;
		}
	}

	// Add Loot Entry to the necessary file
	private synchronized boolean addLootEntry(String npcName, LootRecord rec)
	{
		// Convert entry to JSON
		String dataAsString = RuneLiteAPI.GSON.toJson(rec);

		// Grab file
		String fileName = npcNameToFileName(npcName);
		File lootFile = getOrCreateFile(fileName);

		// Open File in append mode and write new data
		try
		{
			BufferedWriter file = new BufferedWriter(new FileWriter(String.valueOf(lootFile), true));
			file.append(dataAsString);
			file.newLine();
			file.close();
			return true;
		}
		catch (IOException ioe)
		{
			log.warn("Error writing loot data to file {}: {}", fileName, ioe.getMessage());
			return false;
		}
	}

	// Mostly used to adjust previous loot entries (adding pet drops)
	private boolean rewriteLootFile(String npcName, Collection<LootRecord> loots)
	{
		String fileName = npcNameToFileName(npcName);
		File lootFile = getOrCreateFile(fileName);

		// Rewrite the log file (to update the last loot entry)
		try
		{
			BufferedWriter file = new BufferedWriter(new FileWriter(String.valueOf(lootFile), false));
			for ( LootRecord rec : loots)
			{
				// Convert entry to JSON
				String dataAsString = RuneLiteAPI.GSON.toJson(rec);
				file.append(dataAsString);
				file.newLine();
			}
			file.close();

			return true;
		}
		catch (IOException ioe)
		{
			log.warn("Error rewriting loot data to file {}: {}", fileName, ioe.getMessage());
			return false;
		}
	}

	// Delete log file for specified npc name
	private synchronized boolean clearLogFile(String npcName)
	{
		String fileName = npcNameToFileName(npcName);

		File lootFile = new File(playerFolder, fileName);

		if (lootFile.delete())
		{
			log.debug("Deleted loot file: {}", fileName);
			return true;
		}
		else
		{
			log.debug("Couldn't delete file: {}", fileName);
			return false;
		}
	}

	// Public Wrappers
	public Collection<LootRecord> loadData(String npcName)
	{
		return loadLootRecords(npcName);
	}

	public boolean addData(String npcName, LootRecord rec)
	{
		return addLootEntry(npcName, rec);
	}

	public boolean rewriteData(String npcName, Collection<LootRecord> loot)
	{
		return rewriteLootFile(npcName, loot);
	}

	public boolean clearData(String npcName)
	{
		return clearLogFile(npcName);
	}

	public Set<String> getKnownFileNames()
	{
		return fileMap.keySet();
	}
}
