package org.fogbowcloud.manager.core.federatednetwork;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang.NotImplementedException;
import org.fogbowcloud.manager.occi.model.Token;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Created by arnett on 08/02/18.
 */
public class FederatedNetworksDB {

    private static final Gson gson = new Gson();

    private final String databaseFilePath;

    public FederatedNetworksDB(String databaseFilePath) {
        this.databaseFilePath = databaseFilePath;
    }

    private DB openDatabase() {
        return DBMaker.fileDB(new File(databaseFilePath)).make();
    }

    private HTreeMap<String, String> extractHTreeMap(DB database) {
        /* The keys for this map are the userId's and the values are
         * JSONArrays representing the networks for this user */
        DB.HashMapMaker<String, String> userToFedNetworks = database.hashMap(
                "userToFedNetworks", Serializer.STRING, Serializer.STRING);
        return userToFedNetworks.createOrOpen();
    }

    private Set<FederatedNetwork> getFederatedNetworks(HTreeMap<String, String> userIdToFedNetworks, Token.User user) {
    	Set<FederatedNetwork> federatedNetworks;
        if (userIdToFedNetworks.containsKey(user.getId())) {
            String jsonNetworks = userIdToFedNetworks.get(user.getId());
            federatedNetworks = parseFederatedNetworks(jsonNetworks);
        } else {
            federatedNetworks = new HashSet<FederatedNetwork>();
        }

        return federatedNetworks;
    }

    protected Set<FederatedNetwork> parseFederatedNetworks(String jsonArray) {
        Type listType = new TypeToken<Set<FederatedNetwork>>(){}.getType();
        Set<FederatedNetwork> federatedNetworks = gson.fromJson(jsonArray, listType);
        return federatedNetworks;
    }

	public boolean addFederatedNetwork(FederatedNetwork federatedNetwork, Token.User user) {
		DB database = openDatabase();
		HTreeMap<String, String> userIdToFedNetworks = extractHTreeMap(database);

		try {
			Set<FederatedNetwork> federatedNetworks = getFederatedNetworks(userIdToFedNetworks,
					user);
			if (federatedNetworks.contains(federatedNetwork)) {
				federatedNetworks.remove(federatedNetwork);
			}
			federatedNetworks.add(federatedNetwork);
			userIdToFedNetworks.put(user.getId(), gson.toJson(federatedNetworks));
		} finally {
			database.commit();
			database.close();
		}

		return true;
	}

    public boolean delete(String id) {
        throw new NotImplementedException();
    }

    public Set<FederatedNetwork> getUserNetworks(Token.User user) {
        DB database = openDatabase();
        HTreeMap<String, String> userIdToFedNetworks = extractHTreeMap(database);

        try {
            return getFederatedNetworks(userIdToFedNetworks, user);
        } finally {
            database.close();
        }
    }

    public Set<FederatedNetwork> getAllFederatedNetworks() {
        DB database = openDatabase();
        HTreeMap<String, String> userIdToFedNetworks = extractHTreeMap(database);

        Set<FederatedNetwork> allFederatedNetworks = new HashSet<FederatedNetwork>();
        try {
            for (String userId : userIdToFedNetworks.getKeys()) {
                for (FederatedNetwork federatedNetwork : parseFederatedNetworks(userIdToFedNetworks.get(userId))) {
                    allFederatedNetworks.add(federatedNetwork);
                }
            }
        } finally {
            database.close();
        }

        return allFederatedNetworks;
    }

}
