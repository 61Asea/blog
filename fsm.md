# ***大佬写的通用FSM***

之前自己简单的实现过一版FSM，但是存在一个问题就是总是会存在下一个状态的进入依赖于上一个状态的跳转，所以大佬总结后把我之前的transform跳转的方式抽离出来。

这个FSM对象可以进行状态的跳转注册，这样各个状态在不涉及具体业务的情况下，将更加符合开闭原则。

    const log4js = require('log4js');
    const path = require('path');
    const $ = require('underscore');

    class FSM {
        constructor() {
            log4js.getLogger().warn('[obsolete]lib/dp/fsm.js -> lib/dp/fsm/index.js');
            this.idle = false;
        }

        async start(rootPath, config) {
            this.states = $.map(config, r => {
                let state = getState(this, rootPath, r[0]);
                if (r.length <= 2) {
                    let nextState = getState(this, rootPath, r[1]);
                    state.transform = async () => {
                        if (r.length == 2)
                            await this.changeState(nextState);
                        else
                            this.idle = true;
                    };
                } else {
                    let nextStates = $.chain(r).map((cr, i) => {
                        let name = $.isString(cr) && cr || cr.abbr || cr.name;
                        return i ? [name, getState(this, rootPath, cr)] : null;
                    }).filter().object().value();
                    state.transformTo = async name => {
                        if (!nextStates[name])
                            throw new Error(`${r[0]}的下一个状态不存在: ${name}`);

                        await this.changeState(nextStates[name]);
                    };
                }
                return state;
            });

            while (!this.idle) {
                if (this.state)
                    await this.state.transform(this);
                else
                    await this.changeState(this.states[0]);
            }
        }

        async changeState(state) {
            try {
                await state.enter(this);
                this.state = state;
            } catch (ex) {
                this.err = ex;
                this.state = this.states[this.states.length - 1];
                await this.state.enter(this);
            }
        }
    }

    function getState(self, rootPath, item) {
        if (!item)
            return;

        if (!self.nameOfState)
            self.nameOfState = {};

        let name = $.isString(item) && item || item.abbr || item.name;
        if (!self.nameOfState[name]) {
            let filePath = path.join(rootPath, $.isString(item) ? item : item.name);
            let State = require(filePath);
            let args = item.args ? item.args : [];
            self.nameOfState[name] = $.isFunction(State) ? new State(...args) : State;
        }

        return self.nameOfState[name];
    }

    module.exports = FSM;