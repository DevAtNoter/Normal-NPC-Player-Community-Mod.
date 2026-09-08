package net.devatnoter.normalnpcplayer.ai.growth;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared, reference-counted entity-ticking chunk tickets for Player NPCs. */
public final class NPCPlayerChunkManager {
    private static final TicketType<ChunkPos> TYPE = TicketType.create("normal_npc_player", (a,b) -> Long.compare(a.toLong(), b.toLong()));
    private static final int ENTITY_TICKING_LEVEL = 31;
    private static final Map<ServerLevel, Map<Long,Integer>> REFERENCES = new HashMap<>();
    private static final Map<UUID, Set<Long>> OWNED = new HashMap<>();

    private NPCPlayerChunkManager() {}

    public static void tick(ServerLevel level, UUID entityId, ChunkPos center, int radius, boolean enabled) {
        if (!enabled) { releaseAll(level, entityId); return; }
        Set<Long> desired = new HashSet<>();
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) desired.add(new ChunkPos(center.x+x,center.z+z).toLong());
        Set<Long> owned=OWNED.computeIfAbsent(entityId,k->new HashSet<>());
        Map<Long,Integer> refs=REFERENCES.computeIfAbsent(level,k->new HashMap<>());
        for(long key:new HashSet<>(owned)) if(!desired.contains(key)){ChunkPos p=new ChunkPos(key);int n=refs.getOrDefault(key,0)-1;if(n<=0){refs.remove(key);level.getChunkSource().removeRegionTicket(TYPE,p,ENTITY_TICKING_LEVEL,p,true);}else refs.put(key,n);owned.remove(key);}
        for(long key:desired) if(owned.add(key)){ChunkPos p=new ChunkPos(key);int n=refs.getOrDefault(key,0);if(n==0)level.getChunkSource().addRegionTicket(TYPE,p,ENTITY_TICKING_LEVEL,p,true);refs.put(key,n+1);}
    }

    public static void releaseAll(ServerLevel level, UUID entityId) {
        Set<Long> owned=OWNED.remove(entityId);if(owned==null)return;Map<Long,Integer> refs=REFERENCES.get(level);if(refs==null)return;
        for(long key:owned){ChunkPos p=new ChunkPos(key);int n=refs.getOrDefault(key,0)-1;if(n<=0){refs.remove(key);level.getChunkSource().removeRegionTicket(TYPE,p,ENTITY_TICKING_LEVEL,p,true);}else refs.put(key,n);}
        if(refs.isEmpty())REFERENCES.remove(level);
    }
}
