UniqueConnections {
	classvar uniqueConnections;

	*initClass {
		uniqueConnections = MultiLevelIdentityDictionary();
	}

	*at {
		|...path|
		^uniqueConnections.at(*path)
	}

	*put {
		|...pathAndValue|
		uniqueConnections.put(*pathAndValue)
	}
}
