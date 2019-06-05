#! /usr/bin/env python3

from flask import Flask, request, jsonify

app = Flask(__name__)

objects = ['l1', 'l2']

predicates = {
    'light_on': set(['l1']),
    'sun_light': set(['l1'])
}


def switch_light_on(x: str):
    global predicates

    print(f'Before switch_light_on: predicates = {predicates}')
    if (x.lower() not in predicates['light_on']):

        predicates['light_on'].add(x.lower())

        print(f'After switch_light_on: predicates = {predicates}')
        return True

    print('Preconditions not fulfilled!')
    return False


def switch_light_off(x: str):
    global predicates

    print(f'Before switch_light_off: predicates = {predicates}')
    if (x.lower() in predicates['light_on']):

        predicates['light_on'].remove(x.lower())

        print(f'After switch_light_off: predicates = {predicates}')
        return True

    print('Preconditions not fulfilled!')
    return False


actions = {
    'switch_light_off': switch_light_off,
    'switch_light_on': switch_light_on
}


@app.route('/action', methods=['POST'])
def actionHandler():
    """
    Execute an action
    """
    global actions

    if request.is_json:
        json = request.get_json()
        action = json['action']
        if len(json['vars']) == 1:
            x, *_ = json['vars']
            x = x.lower()

            actions[action.lower()](x)

        # TODO: Error handling
        return "OK"


@app.route('/objects', methods=['GET'])
def objectsHandler():
    """
    Returning object-set
    """
    global objects
    return jsonify(objects)


@app.route('/predicates', methods=['GET'])
def predicatesHandler():
    """
    Returning predicates-dict
    """
    global predicates

    p = predicates.copy()

    for key in p:
        if type(p[key]) is not bool:
            p[key] = list(p[key])

    return jsonify(p)


def main():
    app.run(port=5001)


if __name__ == '__main__':
    main()
