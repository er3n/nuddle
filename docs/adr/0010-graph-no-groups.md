# 0010 Graph of 1:1 connections, no groups or roles

Status: Accepted (2026-10-10).

## Decision
- The only relationship structure is a 1:1 connection, stored as two directed `edges` rows with type, direction and learned weights.
- No group, household or role table. "One of you", "the kids" are derived from the sender's own connections.
- Relationship type never grants permissions. It is input for "whom to tell, when".
- The MVP is 1:1 in practice. Graph inference and weights are implemented when a third person appears, but the schema is a graph from day one.
- The LLM receives the sender's connection subgraph as context for extraction.

## Rejected
- **Group / household model:** forces a concept the UI deliberately avoids, and breaks for blended families and roommates.
- **Role based permissions:** contradicts the principle that consent is personal and social.
