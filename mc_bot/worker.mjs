import mc from 'minecraft-protocol';
import { parentPort } from 'worker_threads'

function createBot(ip, port, botName) {
    return new Promise((resolve, reject) => {
        const bot = mc.createClient({
            host: ip,
            port: port,
            username: botName,
            version: "1.16.5"
        });
        let onEnd = (reason) => {
            console.log(`Bot ${bot.username} disconnected: ${reason}`);
            bot.removeListener('playerJoin', onPlayerJoin);
            bot.removeListener('error', onError);
            reject(new Error('Bot disconnected'));
        }
        let onError = (err) => {
            console.error(`Error connecting to server: ${err.message}`);
            bot.removeListener('playerJoin', onPlayerJoin);
            bot.removeListener('end', onEnd);
            reject(err);
        }
        let onPlayerJoin = () => {
            bot.removeListener('error', onError);
            bot.removeListener('end', onEnd);
            resolve(bot);
        }
        bot.once('playerJoin', onPlayerJoin);
        bot.once('error', onError);
        bot.once('end', onEnd);
    });
}

async function getMetrics(bot) {
    return new Promise((resolve, reject) => {
        let obj = {};
        let timeout = setTimeout(() => {
            bot.removeListener('systemChat', fn);
            reject(new Error('Timeout waiting for metrics'));
        }
        , 1000 * 15);
        let currentMs;
        let fn = event => {
            const msgObj = JSON.parse(event.formattedMessage);
            let msg = msgObj.text;
            if (msgObj.extra && msgObj.extra[0] && msgObj.extra[0].text) {
                msg = msgObj.extra[0].text;
            }
            if(msg.toString().includes('ID:')) {
                const id = msg.toString().match(/ID: (\d+)/)[1];
                obj.id = id;
            }

            if(msg.toString().includes('TPS:')) {
                const tps = parseFloat(msg.toString().match(/TPS: (\d+\.\d+)/)[1]);
                obj.tps = tps;
            }
            if(msg.toString().includes('MSPT:')) {
                const mspt = parseFloat(msg.toString().match(/MSPT: (\d+\.\d+)/)[1]);
                obj.mspt = mspt;
            }
            if(obj.id !== undefined && obj.tps !== undefined && obj.mspt !== undefined) {
                bot.removeListener('systemChat', fn);
                obj.latency = Date.now() - currentMs;
                clearTimeout(timeout);
                resolve(obj);
            }
        }

        currentMs = Date.now();

        bot.on('systemChat', fn);
    
        bot.chat('/metrics');
    });
}

const bots = []

parentPort.on('message', async (command) => {
    if (command.type === 'createBot') {
        const { ip, port, botName } = command.data;
        try {
            const bot = await createBot(ip, port, botName);
            const onError = (err) => {
                bots.splice(bots.indexOf(bot), 1);
                bot.removeListener('end', onEnd);
                bot.end();
                parentPort.postMessage({ type: 'error', data: err.message });
            }
            const onEnd = (reason) => {
                bots.splice(bots.indexOf(bot), 1);
                bot.removeListener('error', onError);
                parentPort.postMessage({ type: 'bot_disconnected', data: bot.username });
            }
            bot.once('error', onError);
            bot.once('end', onEnd);
            bots.push(bot);
            parentPort.postMessage({ type: 'botCreated', data: bot.username });
        } catch (error) {
            parentPort.postMessage({ type: 'error', data: error.message });
        }
    }
    else if (command.type === 'getMetrics') {
        const results = [];
        for(const bot of bots) {
            try {
                const metrics = await getMetrics(bot);
                results.push(metrics);
            } catch (error) {
                parentPort.postMessage({ type: 'error', data: error.message });
            }
        }
        parentPort.postMessage({ type: 'metrics', data: results });
    }
    else if (command.type === 'disconnect') {
        for(const bot of bots) {
            bot.end()
        }
        parentPort.postMessage({ type: 'all_disconnected' });
    }
})