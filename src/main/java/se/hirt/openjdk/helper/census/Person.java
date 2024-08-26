/*
 * Copyright (C) 2024 Marcus Hirt
 *                    www.hirt.se
 *
 * This software is free:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESSED OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright (C) Marcus Hirt, 2024
 */
package se.hirt.openjdk.helper.census;

import java.util.HashMap;
import java.util.Map;

public class Person {
	private final String userid;
	private final String fullName;
	private final Map<String, Affiliation> affiliations = new HashMap<>();

	public Person(String userid, String fullName) {
		this.userid = userid;
		this.fullName = fullName;
	}

	public void addAffiliation(String affiliationId, Affiliation affiliation) {
		affiliations.put(affiliationId, affiliation);
	}

	// Getters
	public String getUserid() { return userid; }
	public String getFullName() { return fullName; }
	public Map<String, Affiliation> getAffiliations() { return affiliations; }

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Person{\n\tuserid='").append(userid).append("'\n\tfullName='").append(fullName).append("'\n\taffiliations:");

		for (Affiliation affiliation : affiliations.values()) {
			String role = affiliation.getMembers().get(userid);
			builder.append("\n\t\t").append(affiliation.getClass().getSimpleName()).append(": ").append(affiliation.getFullName()).append(" - ").append(role);
		}
		builder.append("\n}\n");
		return builder.toString();
	}
}
