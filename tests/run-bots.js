/**
 * Run multiple bots to test the sharded server
 */

const mineflayer = require('mineflayer');

const SHARD_ALPHA = 'localhost:25565';
const SHARD_BETA = 'localhost:25566';

console.log('🤖 Starting ShardedMC Bot Test\n');

// Track active bots
const bots = [];
const botNames = [
    { name: 'Steve', port: 25565 },
    { name: 'Alex', port: 25566 },
    { name: 'Notch', port: 25565 },
    { name: 'Herobrine', port: 25566 },
    { name: 'Villager', port: 25565 }
];

function createBot(name, port) {
    const bot = mineflayer.createBot({
        host: 'localhost',
        port: port,
        username: name,
        auth: 'offline',
        checkTimeoutInterval: 60000
    });
    
    bot.on('spawn', () => {
        console.log(`✅ ${name} connected to port ${port}`);
        
        // Random movement every 5 seconds
        setInterval(() => {
            if (bot.entity) {
                const yaw = Math.random() * Math.PI * 2;
                bot.look(yaw, 0, true);
                
                // 50% chance to move forward
                if (Math.random() > 0.5) {
                    bot.setControlState('forward', true);
                    setTimeout(() => {
                        bot.setControlState('forward', false);
                    }, 2000);
                }
            }
        }, 5000);
        
        // Occasionally break blocks
        setInterval(() => {
            if (bot.entity && Math.random() > 0.7) {
                const block = bot.blockAt(bot.entity.position.offset(0, -1, 0));
                if (block && block.name !== 'air') {
                    bot.dig(block).catch(() => {});
                }
            }
        }, 8000);
    });
    
    bot.on('death', () => {
        console.log(`💀 ${name} died`);
        bot.respawn();
    });
    
    bot.on('error', (err) => {
        console.error(`❌ ${name} error:`, err.message);
    });
    
    bot.on('kicked', (reason) => {
        console.log(`👢 ${name} kicked:`, reason);
    });
    
    bot.on('end', () => {
        console.log(`🔌 ${name} disconnected`);
    });
    
    bots.push(bot);
    return bot;
}

// Create all bots with delay
console.log('Creating bots...\n');
botNames.forEach((config, index) => {
    setTimeout(() => {
        console.log(`Creating ${config.name} on port ${config.port}...`);
        createBot(config.name, config.port);
    }, index * 2000);
});

// Status report every 30 seconds
setInterval(() => {
    console.log('\n📊 Status Report:');
    console.log(`Active bots: ${bots.filter(b => b.entity).length}/${bots.length}`);
    bots.forEach(bot => {
        if (bot.entity) {
            console.log(`  ${bot.username}: ${bot.entity.position.toString()}`);
        }
    });
}, 30000);

// Keep running
console.log('Bots will run for 5 minutes...');
setTimeout(() => {
    console.log('\nStopping all bots...');
    bots.forEach(bot => {
        try { bot.end(); } catch(e) {}
    });
    process.exit(0);
}, 300000);

// Handle Ctrl+C
process.on('SIGINT', () => {
    console.log('\nStopping all bots...');
    bots.forEach(bot => {
        try { bot.end(); } catch(e) {}
    });
    process.exit(0);
});
