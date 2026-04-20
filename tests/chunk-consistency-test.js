/**
 * Test to verify chunks are consistent across shards
 */

const mineflayer = require('mineflayer');

console.log('Testing chunk consistency across shards...\n');

function createBot(name, port) {
    return mineflayer.createBot({
        host: 'localhost',
        port: port,
        username: name,
        auth: 'offline',
        checkTimeoutInterval: 60000
    });
}

async function testChunkConsistency() {
    return new Promise((resolve) => {
        const bot1 = createBot('ShardAlphaBot', 25565);
        const bot2 = createBot('ShardBetaBot', 25566);
        
        let spawned1 = false;
        let spawned2 = false;
        
        const checkDone = async () => {
            if (spawned1 && spawned2) {
                console.log('Both bots spawned, waiting for chunks to load...');
                
                // Wait for chunks to load
                await new Promise(r => setTimeout(r, 3000));
                
                // Get ground block at spawn for both bots
                const ground1 = bot1.blockAt(bot1.entity.position.offset(0, -1, 0));
                const ground2 = bot2.blockAt(bot2.entity.position.offset(0, -1, 0));
                
                console.log(`\nShard Alpha spawn: ${bot1.entity.position}`);
                console.log(`Shard Alpha ground: ${ground1 ? ground1.name : 'null'}`);
                console.log(`\nShard Beta spawn: ${bot2.entity.position}`);
                console.log(`Shard Beta ground: ${ground2 ? ground2.name : 'null'}`);
                
                // Also check blocks around spawn
                const checkPos = (bot, label) => {
                    console.log(`\n${label} blocks around spawn:`);
                    for (let dx = -2; dx <= 2; dx++) {
                        for (let dz = -2; dz <= 2; dz++) {
                            const block = bot.blockAt(bot.entity.position.offset(dx, -1, dz));
                            process.stdout.write(block ? (block.name === 'air' ? '.' : '#') : '?');
                        }
                        console.log();
                    }
                };
                
                checkPos(bot1, 'Shard Alpha');
                checkPos(bot2, 'Shard Beta');
                
                console.log('\n✅ Test complete - if terrain looks similar, world is shared!\n');
                
                bot1.end();
                bot2.end();
                resolve();
            }
        };
        
        bot1.on('spawn', () => {
            console.log('Bot 1 spawned on Shard Alpha');
            spawned1 = true;
            checkDone();
        });
        
        bot2.on('spawn', () => {
            console.log('Bot 2 spawned on Shard Beta');
            spawned2 = true;
            checkDone();
        });
        
        setTimeout(() => {
            if (!spawned1 || !spawned2) {
                console.log('Timeout waiting for bots');
            }
            try { bot1.end(); } catch(e) {}
            try { bot2.end(); } catch(e) {}
            resolve();
        }, 15000);
    });
}

testChunkConsistency().then(() => {
    console.log('Done');
    process.exit(0);
});
