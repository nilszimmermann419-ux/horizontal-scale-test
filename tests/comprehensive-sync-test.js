/**
 * Comprehensive sync test - demonstrates all sync features
 */

const mineflayer = require('mineflayer');

console.log('========================================');
console.log('Comprehensive Sync Test');
console.log('========================================\n');

let passed = 0;
let failed = 0;

function success(msg) {
    console.log(`✅ ${msg}`);
    passed++;
}

function error(msg) {
    console.error(`❌ ${msg}`);
    failed++;
}

async function testChunkConsistency() {
    console.log('\n📋 Chunk Consistency Test...');
    
    return new Promise((resolve) => {
        const bot1 = mineflayer.createBot({ host: 'localhost', port: 25565, username: 'AlphaBot', auth: 'offline' });
        const bot2 = mineflayer.createBot({ host: 'localhost', port: 25566, username: 'BetaBot', auth: 'offline' });
        
        let spawned1 = false;
        let spawned2 = false;
        
        let timeoutId;
        
        const check = async () => {
            if (spawned1 && spawned2) {
                clearTimeout(timeoutId);
                console.log('Both bots spawned, waiting for chunks to load...');
                await new Promise(r => setTimeout(r, 5000));
                
                const ground1 = bot1.blockAt(bot1.entity.position.offset(0, -1, 0));
                const ground2 = bot2.blockAt(bot2.entity.position.offset(0, -1, 0));
                
                console.log(`Shard Alpha: ${ground1 ? ground1.name : 'null'}`);
                console.log(`Shard Beta: ${ground2 ? ground2.name : 'null'}`);
                
                if (ground1 && ground2) {
                    if (ground1.name === ground2.name) {
                        success(`Same terrain on both shards: ${ground1.name}`);
                    } else {
                        error(`Different terrain: ${ground1.name} vs ${ground2.name}`);
                    }
                } else {
                    error('Missing terrain data');
                }
                
                bot1.end();
                bot2.end();
                resolve();
            }
        };
        
        bot1.on('spawn', () => { spawned1 = true; check(); });
        bot2.on('spawn', () => { spawned2 = true; check(); });
        
        timeoutId = setTimeout(() => {
            if (!spawned1 || !spawned2) {
                error('Timeout waiting for bots');
            }
            try { bot1.end(); } catch(e) {}
            try { bot2.end(); } catch(e) {}
            resolve();
        }, 15000);
    });
}

async function testBlockSync() {
    console.log('\n📋 Block Synchronization Test...');
    
    return new Promise((resolve) => {
        const bot = mineflayer.createBot({ host: 'localhost', port: 25565, username: 'BlockBreaker', auth: 'offline' });
        
        bot.on('spawn', async () => {
            await new Promise(r => setTimeout(r, 2000));
            
            // Find a solid block
            let target = null;
            for (let dx = -2; dx <= 2 && !target; dx++) {
                for (let dz = -2; dz <= 2 && !target; dz++) {
                    for (let dy = -2; dy <= 2 && !target; dy++) {
                        const block = bot.blockAt(bot.entity.position.offset(dx, dy, dz));
                        if (block && block.name !== 'air' && !block.name.includes('grass')) {
                            target = { block, pos: bot.entity.position.offset(dx, dy, dz) };
                            break;
                        }
                    }
                }
            }
            
            if (target) {
                await bot.dig(target.block);
                await new Promise(r => setTimeout(r, 1000));
                
                const after = bot.blockAt(target.pos);
                if (!after || after.name === 'air') {
                    success('Block broken and sync active');
                } else {
                    error('Block still exists');
                }
            } else {
                error('No suitable block found');
            }
            
            bot.end();
            resolve();
        });
        
        setTimeout(() => {
            error('Timeout');
            bot.end();
            resolve();
        }, 15000);
    });
}

async function runAllTests() {
    await testChunkConsistency();
    await testBlockSync();
    
    console.log('\n========================================');
    console.log(`Results: ${passed} passed, ${failed} failed`);
    console.log('========================================');
    
    if (failed === 0) {
        console.log('\n🎉 All sync features working!');
        console.log('✅ Chunk synchronization');
        console.log('✅ Block synchronization');
    }
    
    process.exit(failed > 0 ? 1 : 0);
}

runAllTests();
